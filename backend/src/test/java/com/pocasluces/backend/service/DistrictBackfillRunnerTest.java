package com.pocasluces.backend.service;

import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.repository.EnelOutageRepository;
import com.pocasluces.backend.repository.EnelOutageRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(EnelOutageRepositoryImpl.class)
class DistrictBackfillRunnerTest {

    @Autowired
    private EnelOutageRepository repository;

    private DistrictLocator districtLocator;
    private DistrictBackfillRunner runner;

    void setUpRunner() {
        districtLocator = new DistrictLocator(new NeighborhoodLocator());
        districtLocator.loadDistricts();
        runner = new DistrictBackfillRunner(repository, districtLocator);
    }

    @Test
    void shouldBackfillDistrictNameForExistingRowsWithValidCoordinates() {
        setUpRunner();
        EnelOutage outage = outage("1", LocalDateTime.of(2026, 7, 10, 8, 30));
        // Coordinates inside the San Pablo-Santa Justa district polygon
        outage.setLatitude(37.394512);
        outage.setLongitude(-5.960205);
        outage.setNeighborhoodName("San Pablo");
        outage.setDistrictName(null);
        repository.save(outage);

        runner.backfill();

        Optional<EnelOutage> found = repository.findByObjectId("1");
        assertThat(found).isPresent();
        assertThat(found.get().getDistrictName()).isEqualTo("San Pablo-Santa Justa");
    }

    @Test
    void shouldBeIdempotent() {
        setUpRunner();
        EnelOutage outage = outage("1", LocalDateTime.of(2026, 7, 10, 8, 30));
        outage.setLatitude(37.394512);
        outage.setLongitude(-5.960205);
        outage.setNeighborhoodName("San Pablo");
        outage.setDistrictName(null);
        repository.save(outage);

        runner.backfill();
        runner.backfill();

        Optional<EnelOutage> found = repository.findByObjectId("1");
        assertThat(found).isPresent();
        assertThat(found.get().getDistrictName()).isEqualTo("San Pablo-Santa Justa");
    }

    @Test
    void shouldSaveUnknownDistrictForNullCoordinates() {
        setUpRunner();
        EnelOutage outage = outage("1", LocalDateTime.of(2026, 7, 10, 8, 30));
        outage.setLatitude(null);
        outage.setLongitude(null);
        outage.setNeighborhoodName("San Pablo");
        outage.setDistrictName(null);
        repository.save(outage);

        runner.backfill();

        Optional<EnelOutage> found = repository.findByObjectId("1");
        assertThat(found).isPresent();
        assertThat(found.get().getDistrictName()).isEqualTo("Zona no identificada");
    }

    @Test
    void shouldSaveUnknownDistrictForZeroZeroCoordinates() {
        setUpRunner();
        EnelOutage outage = outage("1", LocalDateTime.of(2026, 7, 10, 8, 30));
        outage.setLatitude(0.0);
        outage.setLongitude(0.0);
        outage.setNeighborhoodName("San Pablo");
        outage.setDistrictName(null);
        repository.save(outage);

        runner.backfill();

        Optional<EnelOutage> found = repository.findByObjectId("1");
        assertThat(found).isPresent();
        assertThat(found.get().getDistrictName()).isEqualTo("Zona no identificada");
    }

    @Test
    void shouldSkipRowsThatAlreadyHaveDistrictName() {
        setUpRunner();
        EnelOutage outage = outage("1", LocalDateTime.of(2026, 7, 10, 8, 30));
        outage.setLatitude(37.394512);
        outage.setLongitude(-5.960205);
        outage.setNeighborhoodName("San Pablo");
        outage.setDistrictName("Triana");
        repository.save(outage);

        runner.backfill();

        Optional<EnelOutage> found = repository.findByObjectId("1");
        assertThat(found).isPresent();
        assertThat(found.get().getDistrictName()).isEqualTo("Triana");
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
