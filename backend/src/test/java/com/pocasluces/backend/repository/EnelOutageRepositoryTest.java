package com.pocasluces.backend.repository;

import com.pocasluces.backend.entity.EnelOutage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class EnelOutageRepositoryTest {

    @Autowired
    private EnelOutageRepository repository;

    @Autowired
    private TestEntityManager em;

    @Test
    void shouldFindByObjectId() {
        EnelOutage outage = outage(100L, LocalDateTime.of(2026, 7, 10, 8, 30));
        em.persist(outage);

        Optional<EnelOutage> found = repository.findByObjectId(100L);

        assertThat(found).isPresent();
        assertThat(found.get().getObjectId()).isEqualTo(100L);
    }

    @Test
    void shouldFindByYearAndMonth() {
        EnelOutage july = outage(1L, LocalDateTime.of(2026, 7, 10, 8, 30));
        EnelOutage august = outage(2L, LocalDateTime.of(2026, 8, 5, 14, 0));
        em.persist(july);
        em.persist(august);

        List<EnelOutage> result = repository.findByYearAndMonth(2026, 7);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getObjectId()).isEqualTo(1L);
    }

    @Test
    void shouldPaginateByYear() {
        EnelOutage o1 = outage(1L, LocalDateTime.of(2026, 7, 10, 8, 30));
        EnelOutage o2 = outage(2L, LocalDateTime.of(2026, 7, 11, 8, 30));
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

        EnelOutage activeNullReposition = outage(1L, now.minusHours(2));
        activeNullReposition.setRepositionDate(null);
        activeNullReposition.setFetchedAt(now.minusMinutes(10));

        EnelOutage activeFutureReposition = outage(2L, now.minusHours(3));
        activeFutureReposition.setRepositionDate(now.plusHours(2));
        activeFutureReposition.setFetchedAt(now.minusMinutes(10));

        EnelOutage old = outage(3L, now.minusDays(2));
        old.setRepositionDate(now.minusDays(1));
        old.setFetchedAt(now.minusMinutes(10));

        EnelOutage staleFetch = outage(4L, now.minusHours(1));
        staleFetch.setRepositionDate(null);
        staleFetch.setFetchedAt(now.minusHours(10));

        em.persist(activeNullReposition);
        em.persist(activeFutureReposition);
        em.persist(old);
        em.persist(staleFetch);

        List<EnelOutage> result = repository.findCurrentlyActive(now, now.minusHours(6));

        assertThat(result).hasSize(2)
            .extracting(EnelOutage::getObjectId)
            .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void shouldAggregateByMonthAndNeighborhood() {
        EnelOutage o1 = outage(1L, LocalDateTime.of(2026, 7, 10, 8, 30));
        o1.setNeighborhoodName("San Pablo");
        EnelOutage o2 = outage(2L, LocalDateTime.of(2026, 7, 15, 18, 0));
        o2.setNeighborhoodName("San Pablo");
        EnelOutage o3 = outage(3L, LocalDateTime.of(2026, 8, 1, 10, 0));
        o3.setNeighborhoodName("Triana");
        em.persist(o1);
        em.persist(o2);
        em.persist(o3);

        List<Object[]> rows = repository.aggregateByMonthAndNeighborhood(2026);

        assertThat(rows).hasSize(2);
        assertThat(rows).anySatisfy(row -> {
            assertThat(row[0]).isEqualTo(7);
            assertThat(row[1]).isEqualTo("San Pablo");
            assertThat(row[2]).isEqualTo(2L);
        });
    }

    @Test
    void shouldFilterByNeighborhoodIgnoringCase() {
        EnelOutage o1 = outage(1L, LocalDateTime.of(2026, 7, 10, 8, 30));
        o1.setNeighborhoodName("San Pablo");
        EnelOutage o2 = outage(2L, LocalDateTime.of(2026, 7, 11, 8, 30));
        o2.setNeighborhoodName("Triana");
        em.persist(o1);
        em.persist(o2);

        Page<EnelOutage> page = repository.findByNeighborhoodNameIgnoreCase("san pablo",
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "interruptionDate")));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getNeighborhoodName()).isEqualTo("San Pablo");
    }

    private EnelOutage outage(Long objectId, LocalDateTime interruptionDate) {
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
