package com.pocasluces.backend.controller;

import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.repository.EnelOutageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenDataControllerTest {

    private static final String HEADER = "interruption_start,estimated_restoration,observed_end,"
        + "observed_duration_min,affected_supply_points,category,cause,service_type,district,"
        + "neighborhood_approx,latitude,longitude,first_seen,last_seen,active";

    private EnelOutageRepository repo;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repo = mock(EnelOutageRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new OpenDataController(repo)).build();
    }

    @Test
    void shouldReturnCsvWithHeadersBomAndDocumentedColumns() throws Exception {
        when(repo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(resolvedOutage())));

        MvcResult result = mvc.perform(get("/api/open-data/outages.csv"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.matchesPattern("text/csv; ?charset=UTF-8")))
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"sevillasinluz-cortes-all.csv\""))
            .andExpect(header().string("Cache-Control", "public, max-age=300"))
            .andExpect(header().string("Link",
                "<https://creativecommons.org/licenses/by/4.0/>; rel=\"license\""))
            .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);

        String[] lines = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8).split("\r\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).isEqualTo(HEADER);
        assertThat(lines[1]).isEqualTo(
            "2026-09-28T15:38:00,2026-09-28T18:00:00,2026-09-28T15:45:00,7,120,Avería,Avería,GB,"
                + "San Pablo-Santa Justa,San Pablo,37.394512,-5.960205,"
                + "2026-09-28T15:40:00,2026-09-28T15:45:00,false");
    }

    @Test
    void shouldLeaveObservedEndAndDurationEmptyForActiveOutages() throws Exception {
        EnelOutage active = resolvedOutage();
        active.setResolvedAt(null);
        active.setActive(true);
        active.setCause(null);
        when(repo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(active)));

        String body = body(mvc.perform(get("/api/open-data/outages.csv")).andReturn());

        assertThat(body.split("\r\n")[1]).startsWith("2026-09-28T15:38:00,2026-09-28T18:00:00,,,120,Avería,,GB,");
        assertThat(body).endsWith("true\r\n");
    }

    @Test
    void shouldNotLeakRawResponseHashOrSourceUrl() throws Exception {
        EnelOutage o = resolvedOutage();
        o.setRawResponse("{\"secret\":\"raw-payload\"}");
        o.setRawResponseHash("deadbeefhash");
        o.setSourceUrl("http://internal-source");
        when(repo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(o)));

        String body = body(mvc.perform(get("/api/open-data/outages.csv")).andReturn());

        assertThat(body).doesNotContain("raw-payload", "deadbeefhash", "internal-source", "object-1");
    }

    @Test
    void shouldNeutralizeFormulaInjectionInTextFields() throws Exception {
        EnelOutage o = resolvedOutage();
        o.setNeighborhoodName("=HYPERLINK(\"http://evil\")");
        o.setDistrictName("@cmd");
        when(repo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(o)));

        String body = body(mvc.perform(get("/api/open-data/outages.csv")).andReturn());

        assertThat(body).contains(",'@cmd,").contains("\"'=HYPERLINK(\"\"http://evil\"\")\"");
    }

    @Test
    void shouldReadPagesOrderedByInterruptionDateAscendingAndFollowAllPages() throws Exception {
        Pageable first = org.springframework.data.domain.PageRequest.of(0, 1);
        Page<EnelOutage> page0 = new PageImpl<>(List.of(resolvedOutage()), first, 2);
        Page<EnelOutage> page1 = new PageImpl<>(List.of(resolvedOutage()),
            org.springframework.data.domain.PageRequest.of(1, 1), 2);
        when(repo.findAll(any(Pageable.class))).thenReturn(page0, page1);

        String body = body(mvc.perform(get("/api/open-data/outages.csv")).andReturn());

        assertThat(body.split("\r\n")).hasSize(3);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repo, org.mockito.Mockito.times(2)).findAll(captor.capture());
        Sort sort = captor.getAllValues().get(0).getSort();
        assertThat(sort.getOrderFor("interruptionDate").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(sort.getOrderFor("id")).isNotNull();
    }

    @Test
    void shouldFilterByYearAndName() throws Exception {
        when(repo.findByYear(eq(2026), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/open-data/outages.csv?year=2026"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"sevillasinluz-cortes-2026.csv\""));
        verify(repo, never()).findAll(any(Pageable.class));
    }

    @Test
    void shouldFilterByYearAndMonthWithZeroPaddedFilename() throws Exception {
        when(repo.findByYearAndMonth(eq(2026), eq(9), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        MvcResult result = mvc.perform(get("/api/open-data/outages.csv?year=2026&month=9"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"sevillasinluz-cortes-2026-09.csv\""))
            .andReturn();

        assertThat(body(result)).isEqualTo(HEADER + "\r\n");
    }

    @Test
    void shouldRejectMonthWithoutYear() throws Exception {
        mvc.perform(get("/api/open-data/outages.csv?month=9")).andExpect(status().isBadRequest());
        verifyNoInteractions(repo);
    }

    @Test
    void shouldRejectOutOfRangeYearOrMonth() throws Exception {
        mvc.perform(get("/api/open-data/outages.csv?year=1999")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/open-data/outages.csv?year=2026&month=13")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/open-data/outages.csv?year=2026&month=0")).andExpect(status().isBadRequest());
        verifyNoInteractions(repo);
    }

    @Test
    void shouldKeepStandardOutputUnchangedForExplicitCsvFormat() throws Exception {
        when(repo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(resolvedOutage())));
        byte[] plain = mvc.perform(get("/api/open-data/outages.csv")).andReturn().getResponse().getContentAsByteArray();
        byte[] explicit = mvc.perform(get("/api/open-data/outages.csv?format=csv"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"sevillasinluz-cortes-all.csv\""))
            .andReturn().getResponse().getContentAsByteArray();

        assertThat(explicit).isEqualTo(plain);
        assertThat(new String(plain, StandardCharsets.UTF_8)).isEqualTo("﻿" + HEADER + "\r\n"
            + "2026-09-28T15:38:00,2026-09-28T18:00:00,2026-09-28T15:45:00,7,120,Avería,Avería,GB,"
            + "San Pablo-Santa Justa,San Pablo,37.394512,-5.960205,"
            + "2026-09-28T15:40:00,2026-09-28T15:45:00,false\r\n");
    }

    @Test
    void shouldReturnExcelVariantWithSemicolonDecimalCommaAndSpaceDatetimes() throws Exception {
        EnelOutage o = resolvedOutage();
        o.setLatitude(37.40825877);
        o.setNeighborhoodName("Triana; Norte");
        o.setDistrictName("=cmd");
        when(repo.findByYear(eq(2026), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(o)));

        MvcResult result = mvc.perform(get("/api/open-data/outages.csv?year=2026&format=excel"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"sevillasinluz-cortes-2026-excel.csv\""))
            .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        String[] lines = body(result).split("\r\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).isEqualTo(HEADER.replace(',', ';'));
        assertThat(lines[1]).isEqualTo(
            "2026-09-28 15:38:00;2026-09-28 18:00:00;2026-09-28 15:45:00;7;120;Avería;Avería;GB;"
                + "'=cmd;\"Triana; Norte\";37,40825877;-5,960205;"
                + "2026-09-28 15:40:00;2026-09-28 15:45:00;false");
    }

    @Test
    void shouldRejectUnknownFormat() throws Exception {
        mvc.perform(get("/api/open-data/outages.csv?format=xlsx")).andExpect(status().isBadRequest());
        verifyNoInteractions(repo);
    }

    private static String body(MvcResult result) throws Exception {
        byte[] bytes = result.getResponse().getContentAsByteArray();
        return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    }

    private static EnelOutage resolvedOutage() {
        return EnelOutage.builder()
            .id(1L)
            .objectId("object-1")
            .neighborhoodName("San Pablo")
            .districtName("San Pablo-Santa Justa")
            .serviceType("GB")
            .affectedClients(120)
            .latitude(37.394512)
            .longitude(-5.960205)
            .interruptionDate(LocalDateTime.of(2026, 9, 28, 15, 38, 0))
            .repositionDate(LocalDateTime.of(2026, 9, 28, 18, 0, 0))
            .firstSeenAt(LocalDateTime.of(2026, 9, 28, 15, 40, 0))
            .fetchedAt(LocalDateTime.of(2026, 9, 28, 15, 45, 0))
            .resolvedAt(LocalDateTime.of(2026, 9, 28, 15, 45, 0))
            .cause("Avería")
            .active(false)
            .build();
    }
}
