package com.pocasluces.backend.repository;

import com.pocasluces.backend.config.ApiKeyAuth;
import com.pocasluces.backend.controller.ExportController;
import com.pocasluces.backend.controller.OpenDataController;
import com.pocasluces.backend.dto.EnelOutageResponse;
import com.pocasluces.backend.dto.OpenDataOutageRow;
import com.pocasluces.backend.dto.OutageExportDto;
import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.entity.OutageView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Proves that the 2026-10-02 query hygiene change did not alter a single published row:
 * the half-open ranges on {@code interruption_date} select exactly what the former
 * {@code YEAR()} / {@code MONTH()} predicates selected, in the same order; the
 * {@link OutageView} projection carries the same values as the full entity for every DTO;
 * and the slice-based CSV loops produce byte-identical output to the former page-based ones.
 *
 * <p>The fixture sits on every boundary that matters: 00:00:00 on the 1st, 23:59:59 on the
 * last day, December to January, a February 1st, plus a wall-clock that does not exist in
 * Europe/Madrid (DST gap) and one that exists twice (DST overlap). Each scenario runs under
 * several JVM default zones, on top of the two surefire runs (UTC and Europe/Madrid).
 * Concrete subclasses bind the same tests to H2 and to PostgreSQL (Testcontainers, CI).</p>
 */
@DataJpaTest
@ActiveProfiles("dev")
@Import(EnelOutageRepositoryImpl.class)
abstract class AbstractQueryEquivalenceTest {

    private static final Sort DESC = Sort.by(Sort.Direction.DESC, "interruptionDate");
    private static final Sort OPEN_DATA = Sort.by(Sort.Direction.ASC, "interruptionDate").and(Sort.by(Sort.Direction.ASC, "id"));
    private static final int SMALL_PAGE = 3;
    private static final String BOM = "﻿";

    @Autowired
    protected EnelOutageRepository repository;

    @Autowired
    protected LegacyYearMonthQueries legacy;

    @Autowired
    protected TestEntityManager em;

    private TimeZone originalZone;

    static Stream<String> jvmZones() {
        return Stream.of("UTC", "Europe/Madrid", "America/New_York", "Pacific/Auckland");
    }

    @BeforeEach
    void rememberJvmZone() {
        originalZone = TimeZone.getDefault();
    }

    @AfterEach
    void restoreJvmZone() {
        TimeZone.setDefault(originalZone);
    }

    // ---- fixture -----------------------------------------------------------------------------

    /** Distinct starts everywhere, so every "ORDER BY interruption_date" is total and comparable. */
    static final List<LocalDateTime> STARTS = List.of(
        LocalDateTime.of(2025, 12, 31, 23, 59, 59),
        LocalDateTime.of(2026, 1, 1, 0, 0, 0),
        LocalDateTime.of(2026, 1, 1, 0, 0, 1),
        LocalDateTime.of(2026, 1, 31, 23, 59, 59),
        LocalDateTime.of(2026, 2, 1, 0, 0, 0),
        LocalDateTime.of(2026, 2, 28, 23, 59, 59),
        LocalDateTime.of(2026, 3, 1, 0, 0, 0),
        LocalDateTime.of(2026, 3, 29, 2, 30, 0),    // does not exist in Europe/Madrid (DST gap)
        LocalDateTime.of(2026, 6, 15, 12, 0, 0),
        LocalDateTime.of(2026, 10, 25, 2, 30, 0),   // exists twice in Europe/Madrid (DST overlap)
        LocalDateTime.of(2026, 11, 30, 23, 59, 59),
        LocalDateTime.of(2026, 12, 1, 0, 0, 0),
        LocalDateTime.of(2026, 12, 31, 23, 59, 59),
        LocalDateTime.of(2027, 1, 1, 0, 0, 0),
        LocalDateTime.of(2027, 1, 1, 0, 0, 1));

    private static final String[] DISTRICTS = {"Triana", "Nervión", "Casco Antiguo", null, "Macarena"};
    private static final String[] NEIGHBORHOODS = {"León", "Nervión", "San Pablo", "Zona no identificada"};

    private void fixture(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zone)));
        int i = 0;
        for (LocalDateTime start : STARTS) {
            LocalDateTime seen = start.plusMinutes(3);
            EnelOutage o = EnelOutage.builder()
                .objectId("obj-" + i)
                .latitude(37.38 + i * 0.001)
                .longitude(-5.99 - i * 0.001)
                .affectedClients(i % 3 == 0 ? null : 10 * i)
                .serviceType(i % 2 == 0 ? "AT" : "BT")
                .interruptionDate(start)
                .repositionDate(i % 4 == 0 ? null : start.plusHours(2))
                .neighborhoodName(NEIGHBORHOODS[i % NEIGHBORHOODS.length])
                .districtName(DISTRICTS[i % DISTRICTS.length])
                .cause(i % 3 == 0 ? "Trabajos programados" : (i % 3 == 1 ? "Avería" : null))
                .sourceUrl("https://dpa-portalgis.enel.com/.../query?resultOffset=" + i)
                .rawResponse("{\"attributes\":{\"objectid1\":\"" + i + "\"}}")
                .rawResponseHash("hash-" + i)
                .firstSeenAt(seen)
                .fetchedAt(i % 5 == 0 ? seen : seen.plusMinutes(5))
                .createdAt(seen)
                .updatedAt(seen)
                .active(i % 4 != 0)
                .resolvedAt(i % 4 != 0 ? null : (i % 5 == 0 ? seen : seen.plusMinutes(5)))
                .build();
            if (i % 2 == 0) {
                em.persist(o);
            } else {
                repository.upsert(o);   // the production write path (native on PostgreSQL)
            }
            i++;
        }
        em.flush();
        em.clear();
    }

    // ---- lists and aggregation ------------------------------------------------------------------

    @ParameterizedTest(name = "JVM zone {0}")
    @MethodSource("jvmZones")
    void yearAndMonthListsSelectTheSameRowsInTheSameOrder(String zone) {
        fixture(zone);
        for (int year = 2024; year <= 2028; year++) {
            assertThat(ids(repository.findByYear(year))).as("year %d", year).isEqualTo(entityIds(legacy.findByYear(year)));
            for (int month = 1; month <= 12; month++) {
                assertThat(ids(repository.findByYearAndMonth(year, month))).as("%d-%02d", year, month)
                    .isEqualTo(entityIds(legacy.findByYearAndMonth(year, month)));
            }
        }
        // Sanity: the fixture really spans the boundaries, and the order is most recent first.
        assertThat(repository.findByYear(2026)).hasSize(12);
        assertThat(repository.findByYearAndMonth(2026, 1)).extracting(OutageView::getInterruptionDate).containsExactly(
            LocalDateTime.of(2026, 1, 31, 23, 59, 59), LocalDateTime.of(2026, 1, 1, 0, 0, 1), LocalDateTime.of(2026, 1, 1, 0, 0, 0));
        assertThat(repository.findByYearAndMonth(2025, 12)).extracting(OutageView::getInterruptionDate)
            .containsExactly(LocalDateTime.of(2025, 12, 31, 23, 59, 59));
        assertThat(repository.findByYearAndMonth(2027, 1)).hasSize(2);
        assertThat(repository.findByYearAndMonth(2026, 4)).isEmpty();
    }

    @ParameterizedTest(name = "JVM zone {0}")
    @MethodSource("jvmZones")
    void monthlyAggregationIsIdentical(String zone) {
        fixture(zone);
        for (int year = 2025; year <= 2027; year++) {
            List<List<Object>> expected = legacy.aggregateByMonthAndDistrict(year).stream().map(List::of).toList();
            List<List<Object>> actual = repository.aggregateByMonthAndDistrict(year).stream().map(List::of).toList();
            assertThat(actual).as("year %d", year).isEqualTo(expected);
        }
        assertThat(repository.aggregateByMonthAndDistrict(2026)).isNotEmpty();
    }

    // ---- paged endpoints -------------------------------------------------------------------------

    @ParameterizedTest(name = "JVM zone {0}")
    @MethodSource("jvmZones")
    void pagedYearAndMonthQueriesMatchPageByPageIncludingTotals(String zone) {
        fixture(zone);
        for (int year = 2025; year <= 2027; year++) {
            int y = year;
            assertSamePages(p -> legacy.findByYear(y, p), p -> repository.findByYear(y, p), DESC, "year " + year);
            for (int month = 1; month <= 12; month++) {
                int m = month;
                assertSamePages(p -> legacy.findByYearAndMonth(y, m, p), p -> repository.findByYearAndMonth(y, m, p),
                    DESC, year + "-" + month);
            }
        }
        assertSamePages(repository::findAll, repository::findAllProjectedBy, DESC, "all");
        assertSamePages(p -> legacy.findByNeighborhoodNameIgnoreCase("san pablo", p),
            p -> repository.findByNeighborhoodNameIgnoreCase("san pablo", p), DESC, "neighborhood");
    }

    @ParameterizedTest(name = "JVM zone {0}")
    @MethodSource("jvmZones")
    void sliceLoopsVisitTheSameRowsInTheSameOrderAsThePageLoops(String zone) {
        fixture(zone);
        assertSameSequence(repository::findAll, repository::findSliceBy, OPEN_DATA, "all asc");
        assertSameSequence(repository::findAll, repository::findSliceBy, DESC, "all desc");
        for (int year = 2025; year <= 2027; year++) {
            int y = year;
            assertSameSequence(p -> legacy.findByYear(y, p), p -> repository.findSliceByYear(y, p), OPEN_DATA, "year asc " + year);
            assertSameSequence(p -> legacy.findByYear(y, p), p -> repository.findSliceByYear(y, p), DESC, "year desc " + year);
            for (int month = 1; month <= 12; month++) {
                int m = month;
                assertSameSequence(p -> legacy.findByYearAndMonth(y, m, p), p -> repository.findSliceByYearAndMonth(y, m, p),
                    OPEN_DATA, "month asc " + year + "-" + month);
                assertSameSequence(p -> legacy.findByYearAndMonth(y, m, p), p -> repository.findSliceByYearAndMonth(y, m, p),
                    DESC, "month desc " + year + "-" + month);
            }
        }
    }

    // ---- projection vs entity ---------------------------------------------------------------------

    @ParameterizedTest(name = "JVM zone {0}")
    @MethodSource("jvmZones")
    void projectionFeedsEveryDtoWithExactlyTheEntitysValues(String zone) {
        fixture(zone);
        Map<Long, EnelOutage> entities = repository.findAll().stream().collect(Collectors.toMap(EnelOutage::getId, Function.identity()));
        List<OutageView> views = repository.findAllProjectedBy(Pageable.unpaged()).getContent();
        assertThat(views).hasSize(STARTS.size());
        for (OutageView view : views) {
            EnelOutage entity = entities.get(view.getId());
            assertThat(entity).isNotNull();
            assertThat(EnelOutageResponse.from(view)).isEqualTo(EnelOutageResponse.from(entity));
            assertThat(OutageExportDto.from(view)).isEqualTo(OutageExportDto.from(entity));
            for (OpenDataOutageRow.Variant variant : OpenDataOutageRow.Variant.values()) {
                assertThat(OpenDataOutageRow.toCsvRow(view, variant)).isEqualTo(OpenDataOutageRow.toCsvRow(entity, variant));
            }
            assertThat(view.isBrief()).isEqualTo(entity.isBrief());
            assertThat(entity.getRawResponse()).as("the entity path still loads raw_response").isNotNull();
        }
        // Both brief and non-brief rows, resolved and active ones, are in the fixture.
        assertThat(views).anyMatch(OutageView::isBrief).anyMatch(v -> !v.isBrief())
            .anyMatch(OutageView::isActive).anyMatch(v -> !v.isActive());
    }

    // ---- controllers end to end -------------------------------------------------------------------

    @ParameterizedTest(name = "JVM zone {0}")
    @MethodSource("jvmZones")
    void openDataCsvIsByteIdenticalToTheFormerPageBasedDownload(String zone) throws Exception {
        fixture(zone);
        OpenDataController controller = new OpenDataController(repository);
        for (Integer[] scope : new Integer[][] {{null, null}, {2026, null}, {2026, 1}, {2026, 12}, {2025, 12}, {2027, 1}, {2026, 4}}) {
            for (String format : new String[] {null, "csv", "excel"}) {
                OpenDataOutageRow.Variant variant = "excel".equals(format)
                    ? OpenDataOutageRow.Variant.EXCEL : OpenDataOutageRow.Variant.STANDARD;
                MockHttpServletResponse response = new MockHttpServletResponse();
                controller.outagesCsv(scope[0], scope[1], format, response);

                StringBuilder expected = new StringBuilder(BOM).append(OpenDataOutageRow.header(variant)).append("\r\n");
                for (EnelOutage o : legacyRows(scope[0], scope[1], OPEN_DATA)) {
                    expected.append(OpenDataOutageRow.toCsvRow(o, variant)).append("\r\n");
                }
                assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                    .as("open data %s/%s %s", scope[0], scope[1], format).isEqualTo(expected.toString());
            }
        }
    }

    @ParameterizedTest(name = "JVM zone {0}")
    @MethodSource("jvmZones")
    void adminExportCsvIsIdenticalToTheFormerPageBasedExport(String zone) throws Exception {
        fixture(zone);
        ExportController controller = new ExportController(repository, mock(ApiKeyAuth.class));
        for (Integer[] scope : new Integer[][] {{null, null}, {2026, null}, {2026, 1}, {2026, 12}, {2025, 12}, {2027, 1}, {2026, 4}}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            controller.exportCsv(scope[0], scope[1], new MockHttpServletRequest(), response);

            StringBuilder expected = new StringBuilder(OutageExportDto.header()).append(System.lineSeparator());
            for (EnelOutage o : legacyRows(scope[0], scope[1], DESC)) {
                expected.append(OutageExportDto.from(o).toCsvRow()).append(System.lineSeparator());
            }
            assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .as("export %s/%s", scope[0], scope[1]).isEqualTo(expected.toString());
        }
    }

    // ---- helpers ----------------------------------------------------------------------------------

    /** All rows of a scope through the former page-based loop over full entities. */
    private List<EnelOutage> legacyRows(Integer year, Integer month, Sort sort) {
        Function<Pageable, Page<EnelOutage>> reader;
        if (year == null) {
            reader = repository::findAll;
        } else if (month == null) {
            reader = p -> legacy.findByYear(year, p);
        } else {
            reader = p -> legacy.findByYearAndMonth(year, month, p);
        }
        List<EnelOutage> rows = new ArrayList<>();
        Pageable pageable = PageRequest.of(0, SMALL_PAGE, sort);
        Page<EnelOutage> page;
        do {
            page = reader.apply(pageable);
            rows.addAll(page.getContent());
            pageable = page.nextPageable();
        } while (page.hasNext());
        return rows;
    }

    private static void assertSamePages(Function<Pageable, Page<EnelOutage>> old, Function<Pageable, Page<OutageView>> now,
                                        Sort sort, String what) {
        Pageable pageable = PageRequest.of(0, SMALL_PAGE, sort);
        Page<EnelOutage> oldPage;
        Page<OutageView> newPage;
        int pages = 0;
        do {
            oldPage = old.apply(pageable);
            newPage = now.apply(pageable);
            assertThat(newPage.getTotalElements()).as("%s page %d total", what, pages).isEqualTo(oldPage.getTotalElements());
            assertThat(newPage.getTotalPages()).as("%s page %d pages", what, pages).isEqualTo(oldPage.getTotalPages());
            assertThat(ids(newPage.getContent())).as("%s page %d", what, pages).isEqualTo(entityIds(oldPage.getContent()));
            assertThat(newPage.hasNext()).as("%s page %d hasNext", what, pages).isEqualTo(oldPage.hasNext());
            pageable = oldPage.nextPageable();
            pages++;
        } while (oldPage.hasNext());
    }

    private static void assertSameSequence(Function<Pageable, Page<EnelOutage>> old, Function<Pageable, Slice<OutageView>> now,
                                           Sort sort, String what) {
        List<Long> expected = new ArrayList<>();
        Pageable pageable = PageRequest.of(0, SMALL_PAGE, sort);
        Page<EnelOutage> page;
        do {
            page = old.apply(pageable);
            expected.addAll(entityIds(page.getContent()));
            pageable = page.nextPageable();
        } while (page.hasNext());

        List<Long> actual = new ArrayList<>();
        pageable = PageRequest.of(0, SMALL_PAGE, sort);
        Slice<OutageView> slice;
        int slices = 0;
        do {
            slice = now.apply(pageable);
            assertThat(slice.getContent().size()).as("%s slice %d size", what, slices).isLessThanOrEqualTo(SMALL_PAGE);
            actual.addAll(ids(slice.getContent()));
            pageable = slice.nextPageable();
            slices++;
        } while (slice.hasNext());

        assertThat(actual).as(what).isEqualTo(expected);
    }

    private static List<Long> ids(List<OutageView> rows) {
        return rows.stream().map(OutageView::getId).toList();
    }

    private static List<Long> entityIds(List<EnelOutage> rows) {
        return rows.stream().map(EnelOutage::getId).toList();
    }
}
