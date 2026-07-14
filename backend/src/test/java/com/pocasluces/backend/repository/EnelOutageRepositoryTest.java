package com.pocasluces.backend.repository;

import com.pocasluces.backend.entity.EnelOutage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(EnelOutageRepositoryImpl.class)
class EnelOutageRepositoryTest {

    @Autowired
    private EnelOutageRepository repository;

    @Autowired
    private TestEntityManager em;

    @Test
    void shouldFindByObjectId() {
        EnelOutage outage = outage("100", LocalDateTime.of(2026, 7, 10, 8, 30));
        em.persist(outage);

        Optional<EnelOutage> found = repository.findByObjectId("100");

        assertThat(found).isPresent();
        assertThat(found.get().getObjectId()).isEqualTo("100");
    }

    @Test
    void shouldFindByYearAndMonth() {
        EnelOutage july = outage("1", LocalDateTime.of(2026, 7, 10, 8, 30));
        EnelOutage august = outage("2", LocalDateTime.of(2026, 8, 5, 14, 0));
        em.persist(july);
        em.persist(august);

        List<EnelOutage> result = repository.findByYearAndMonth(2026, 7);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getObjectId()).isEqualTo("1");
    }

    @Test
    void shouldPaginateByYear() {
        EnelOutage o1 = outage("1", LocalDateTime.of(2026, 7, 10, 8, 30));
        EnelOutage o2 = outage("2", LocalDateTime.of(2026, 7, 11, 8, 30));
        em.persist(o1);
        em.persist(o2);

        Page<EnelOutage> page = repository.findByYear(2026,
            PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "interruptionDate")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void shouldFindCurrentlyActiveIncludingNullReposition() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 10, 12, 0);

        EnelOutage activeNullReposition = outage("1", now.minusHours(2));
        activeNullReposition.setRepositionDate(null);
        activeNullReposition.setFetchedAt(now.minusMinutes(10));

        EnelOutage activeFutureReposition = outage("2", now.minusHours(3));
        activeFutureReposition.setRepositionDate(now.plusHours(2));
        activeFutureReposition.setFetchedAt(now.minusMinutes(10));

        EnelOutage old = outage("3", now.minusDays(2));
        old.setRepositionDate(now.minusDays(1));
        old.setFetchedAt(now.minusMinutes(10));

        EnelOutage staleFetch = outage("4", now.minusHours(1));
        staleFetch.setRepositionDate(null);
        staleFetch.setFetchedAt(now.minusHours(10));

        EnelOutage inactive = outage("5", now.minusHours(4));
        inactive.setRepositionDate(null);
        inactive.setFetchedAt(now.minusMinutes(10));
        inactive.setActive(false);

        em.persist(activeNullReposition);
        em.persist(activeFutureReposition);
        em.persist(old);
        em.persist(staleFetch);
        em.persist(inactive);

        List<EnelOutage> result = repository.findCurrentlyActive(now, now.minusHours(6));

        assertThat(result).hasSize(2)
            .extracting(EnelOutage::getObjectId)
            .containsExactlyInAnyOrder("1", "2");
    }

    @Test
    void shouldAggregateByMonthAndDistrict() {
        EnelOutage o1 = outage("1", LocalDateTime.of(2026, 7, 10, 8, 30));
        o1.setNeighborhoodName("San Pablo");
        o1.setDistrictName("San Pablo-Santa Justa");
        EnelOutage o2 = outage("2", LocalDateTime.of(2026, 7, 15, 18, 0));
        o2.setNeighborhoodName("San Pablo");
        o2.setDistrictName("San Pablo-Santa Justa");
        EnelOutage o3 = outage("3", LocalDateTime.of(2026, 8, 1, 10, 0));
        o3.setNeighborhoodName("Triana");
        o3.setDistrictName("Triana");
        em.persist(o1);
        em.persist(o2);
        em.persist(o3);

        List<Object[]> rows = repository.aggregateByMonthAndDistrict(2026);

        assertThat(rows).hasSize(2);
        assertThat(rows).anySatisfy(row -> {
            assertThat(row[0]).isEqualTo(7);
            assertThat(row[1]).isEqualTo("San Pablo-Santa Justa");
            assertThat(row[2]).isEqualTo(2L);
        });
    }

    @Test
    void shouldFilterByNeighborhoodIgnoringCase() {
        EnelOutage o1 = outage("1", LocalDateTime.of(2026, 7, 10, 8, 30));
        o1.setNeighborhoodName("San Pablo");
        EnelOutage o2 = outage("2", LocalDateTime.of(2026, 7, 11, 8, 30));
        o2.setNeighborhoodName("Triana");
        em.persist(o1);
        em.persist(o2);

        Page<EnelOutage> page = repository.findByNeighborhoodNameIgnoreCase("san pablo",
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "interruptionDate")));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getNeighborhoodName()).isEqualTo("San Pablo");
    }

    @Test
    void shouldAtomicallyUpsertNewOutage() {
        EnelOutage outage = outage("123", LocalDateTime.of(2026, 7, 10, 8, 30));
        outage.setNeighborhoodName("San Pablo");
        outage.setDistrictName("San Pablo-Santa Justa");
        outage.setServiceType("AT");
        outage.setFirstSeenAt(LocalDateTime.of(2026, 7, 10, 8, 0));
        outage.setCreatedAt(LocalDateTime.of(2026, 7, 10, 8, 0));
        outage.setFetchedAt(LocalDateTime.of(2026, 7, 10, 8, 0));
        outage.setUpdatedAt(LocalDateTime.of(2026, 7, 10, 8, 0));

        int rows = repository.upsert(outage);

        assertThat(rows).isEqualTo(1);
        Optional<EnelOutage> found = repository.findByNeighborhoodNameAndInterruptionDateAndServiceType(
            "San Pablo", LocalDateTime.of(2026, 7, 10, 8, 30), "AT");
        assertThat(found).isPresent();
        assertThat(found.get().getObjectId()).isEqualTo("123");
        assertThat(found.get().getDistrictName()).isEqualTo("San Pablo-Santa Justa");
        assertThat(found.get().getFirstSeenAt()).isEqualTo(LocalDateTime.of(2026, 7, 10, 8, 0));
    }

    @Test
    void shouldAtomicallyUpsertExistingOutageAndPreserveFirstSeenAt() {
        EnelOutage original = outage("100", LocalDateTime.of(2026, 7, 10, 8, 30));
        original.setNeighborhoodName("San Pablo");
        original.setDistrictName("San Pablo-Santa Justa");
        original.setServiceType("AT");
        original.setFirstSeenAt(LocalDateTime.of(2026, 7, 1, 0, 0));
        original.setCreatedAt(LocalDateTime.of(2026, 7, 1, 0, 0));
        original.setFetchedAt(LocalDateTime.of(2026, 7, 1, 0, 0));
        original.setUpdatedAt(LocalDateTime.of(2026, 7, 1, 0, 0));
        repository.upsert(original);

        EnelOutage update = outage("200", LocalDateTime.of(2026, 7, 10, 8, 30));
        update.setNeighborhoodName("San Pablo");
        update.setDistrictName("Triana");
        update.setServiceType("AT");
        update.setFirstSeenAt(LocalDateTime.of(2026, 7, 10, 12, 0));
        update.setCreatedAt(LocalDateTime.of(2026, 7, 10, 12, 0));
        update.setFetchedAt(LocalDateTime.of(2026, 7, 10, 12, 0));
        update.setUpdatedAt(LocalDateTime.of(2026, 7, 10, 12, 0));

        int rows = repository.upsert(update);

        assertThat(rows).isEqualTo(1);
        em.flush();
        em.clear();
        Optional<EnelOutage> found = repository.findByNeighborhoodNameAndInterruptionDateAndServiceType(
            "San Pablo", LocalDateTime.of(2026, 7, 10, 8, 30), "AT");
        assertThat(found).isPresent();
        assertThat(found.get().getObjectId()).isEqualTo("200");
        assertThat(found.get().getDistrictName()).isEqualTo("Triana");
        assertThat(found.get().getFirstSeenAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(found.get().getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(found.get().getFetchedAt()).isEqualTo(LocalDateTime.of(2026, 7, 10, 12, 0));
        assertThat(found.get().getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 10, 12, 0));
    }

    @Test
    void shouldPreventDuplicateNaturalKeyOnConcurrentUpsert() {
        EnelOutage outage = outage("1", LocalDateTime.of(2026, 7, 10, 8, 30));
        outage.setNeighborhoodName("San Pablo");
        outage.setServiceType("AT");

        repository.upsert(outage);
        repository.upsert(outage);

        long count = em.getEntityManager().createQuery(
                "SELECT COUNT(o) FROM EnelOutage o WHERE o.neighborhoodName = :name " +
                "AND o.interruptionDate = :date AND o.serviceType = :type", Long.class)
            .setParameter("name", "San Pablo")
            .setParameter("date", LocalDateTime.of(2026, 7, 10, 8, 30))
            .setParameter("type", "AT")
            .getSingleResult();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldSetAllInactive() {
        EnelOutage o1 = outage("1", LocalDateTime.of(2026, 7, 10, 8, 30));
        EnelOutage o2 = outage("2", LocalDateTime.of(2026, 7, 11, 8, 30));
        em.persist(o1);
        em.persist(o2);

        repository.setAllInactive();
        em.flush();
        em.clear();

        assertThat(repository.findAll()).extracting(EnelOutage::isActive).containsOnly(false);
    }

    @Test
    void shouldSetActiveByObjectIds() {
        EnelOutage o1 = outage("1", LocalDateTime.of(2026, 7, 10, 8, 30));
        EnelOutage o2 = outage("2", LocalDateTime.of(2026, 7, 11, 8, 30));
        o2.setActive(false);
        em.persist(o1);
        em.persist(o2);

        repository.setActiveByObjectIds(List.of("2"), true);
        em.flush();
        em.clear();

        assertThat(repository.findByObjectId("1")).isPresent().hasValueSatisfying(o -> assertThat(o.isActive()).isTrue());
        assertThat(repository.findByObjectId("2")).isPresent().hasValueSatisfying(o -> assertThat(o.isActive()).isTrue());
    }

    @Test
    void shouldTolerateEmptyObjectIdListWhenSettingActive() {
        repository.setActiveByObjectIds(List.of(), true);
    }

    private EnelOutage outage(String objectId, LocalDateTime interruptionDate) {
        LocalDateTime now = LocalDateTime.now();
        return EnelOutage.builder()
            .objectId(objectId)
            .interruptionDate(interruptionDate)
            .serviceType("GB")
            .neighborhoodName("San Pablo")
            .fetchedAt(now)
            .firstSeenAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
