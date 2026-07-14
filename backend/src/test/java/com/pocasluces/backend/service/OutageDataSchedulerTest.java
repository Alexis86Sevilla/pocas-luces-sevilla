package com.pocasluces.backend.service;

import com.pocasluces.backend.dto.EnelApiFeatureWithEvidence;
import com.pocasluces.backend.dto.EnelApiResponse;
import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.repository.EnelOutageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutageDataSchedulerTest {

    @Mock
    private EnelApiService enelApiService;

    @Mock
    private EnelOutageRepository repository;

    @Mock
    private NeighborhoodLocator locator;

    @Mock
    private DistrictLocator districtLocator;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneId.of("UTC"));
    private OutageDataScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutageDataScheduler(enelApiService, repository, locator, districtLocator, clock);
    }

    @Test
    void shouldInsertNewOutageAndSetFirstSeenAt() {
        EnelApiResponse.Feature feature = feature("123", "10/07/2026 08:30", 37.3970, -5.9800, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(new EnelApiFeatureWithEvidence(feature, "http://source", "{\"raw\":\"data\"}")));
        when(locator.findNeighborhood(37.3970, -5.9800)).thenReturn("San Pablo");
        when(districtLocator.findDistrict(37.3970, -5.9800, "San Pablo")).thenReturn("San Pablo-Santa Justa");

        scheduler.fetchAndSaveOutages();

        ArgumentCaptor<EnelOutage> captor = ArgumentCaptor.forClass(EnelOutage.class);
        verify(repository).upsert(captor.capture());
        EnelOutage saved = captor.getValue();

        assertThat(saved.getObjectId()).isEqualTo("123");
        assertThat(saved.getNeighborhoodName()).isEqualTo("San Pablo");
        assertThat(saved.getDistrictName()).isEqualTo("San Pablo-Santa Justa");
        assertThat(saved.getServiceType()).isEqualTo("AT");
        assertThat(saved.getInterruptionDate()).isEqualTo(LocalDateTime.of(2026, 7, 10, 8, 30));
        assertThat(saved.getFetchedAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(saved.getFirstSeenAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(saved.getCreatedAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(saved.getRawResponse()).isEqualTo("{\"raw\":\"data\"}");
        assertThat(saved.getRawResponseHash()).hasSize(64);
        assertThat(saved.getSourceUrl()).isEqualTo("http://source");
    }

    @Test
    void shouldPassCurrentTimestampsToAtomicUpsert() {
        EnelApiResponse.Feature feature = feature("456", "10/07/2026 08:30", 37.3970, -5.9800, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(new EnelApiFeatureWithEvidence(feature, "http://source", "{\"raw\":\"data\"}")));
        when(locator.findNeighborhood(37.3970, -5.9800)).thenReturn("San Pablo");
        when(districtLocator.findDistrict(37.3970, -5.9800, "San Pablo")).thenReturn("San Pablo-Santa Justa");

        scheduler.fetchAndSaveOutages();

        ArgumentCaptor<EnelOutage> captor = ArgumentCaptor.forClass(EnelOutage.class);
        verify(repository).upsert(captor.capture());
        EnelOutage upserted = captor.getValue();

        assertThat(upserted.getObjectId()).isEqualTo("456");
        assertThat(upserted.getFirstSeenAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(upserted.getCreatedAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(upserted.getUpdatedAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(upserted.getFetchedAt()).isEqualTo(LocalDateTime.now(clock));
    }

    @Test
    void shouldSkipOutageWithNullObjectId() {
        EnelApiResponse.Feature feature = feature(null, "10/07/2026 08:30", 37.0, -5.0, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(new EnelApiFeatureWithEvidence(feature, "http://source", "{}")));

        scheduler.fetchAndSaveOutages();

        verify(repository, never()).upsert(any());
    }

    @Test
    void shouldSkipOutageWithUnparseableInterruptionDate() {
        EnelApiResponse.Feature feature = feature("123", "not-a-date", 37.0, -5.0, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(new EnelApiFeatureWithEvidence(feature, "http://source", "{}")));

        scheduler.fetchAndSaveOutages();

        verify(repository, never()).upsert(any());
    }

    @Test
    void shouldAbortWhenApiThrows() {
        when(enelApiService.fetchSevillaOutages())
            .thenThrow(new EnelApiService.EnelApiException("API down"));

        scheduler.fetchAndSaveOutages();

        verify(repository, never()).setAllInactive();
        verify(repository, never()).upsert(any());
    }

    @Test
    void shouldMarkAllInactiveBeforeUpsertingFetchedOutages() {
        EnelApiResponse.Feature feature = feature("123", "10/07/2026 08:30", 37.3970, -5.9800, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(new EnelApiFeatureWithEvidence(feature, "http://source", "{}")));
        when(locator.findNeighborhood(37.3970, -5.9800)).thenReturn("San Pablo");
        when(districtLocator.findDistrict(37.3970, -5.9800, "San Pablo")).thenReturn("San Pablo-Santa Justa");

        scheduler.fetchAndSaveOutages();

        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).setAllInactive();
        inOrder.verify(repository).upsert(any());

        ArgumentCaptor<EnelOutage> captor = ArgumentCaptor.forClass(EnelOutage.class);
        verify(repository).upsert(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void shouldMarkAllInactiveWhenFetchReturnsNoOutages() {
        when(enelApiService.fetchSevillaOutages()).thenReturn(List.of());

        scheduler.fetchAndSaveOutages();

        verify(repository).setAllInactive();
        verify(repository, never()).upsert(any());
    }

    @Test
    void shouldTolerateObjectIdChangeForSameNaturalKey() {
        EnelApiResponse.Feature first = feature("100", "10/07/2026 08:30", 37.3970, -5.9800, "AT");
        EnelApiResponse.Feature second = feature("200", "10/07/2026 08:30", 37.3970, -5.9800, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(
                new EnelApiFeatureWithEvidence(first, "http://page1", "{\"id\":100}"),
                new EnelApiFeatureWithEvidence(second, "http://page2", "{\"id\":200}")
            ));
        when(locator.findNeighborhood(37.3970, -5.9800)).thenReturn("San Pablo");
        when(districtLocator.findDistrict(37.3970, -5.9800, "San Pablo")).thenReturn("San Pablo-Santa Justa");

        scheduler.fetchAndSaveOutages();

        // Two upserts should be issued for the same natural key; the repository upsert
        // is atomic and the second call simply updates the row to objectId 200.
        verify(repository, times(2)).upsert(any());
    }

    @Test
    void shouldFallbackToUnknownDistrictWhenLocatorReturnsNull() {
        EnelApiResponse.Feature feature = feature("123", "10/07/2026 08:30", 37.3970, -5.9800, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(new EnelApiFeatureWithEvidence(feature, "http://source", "{}")));
        when(locator.findNeighborhood(37.3970, -5.9800)).thenReturn("San Pablo");
        when(districtLocator.findDistrict(37.3970, -5.9800, "San Pablo")).thenReturn(null);

        scheduler.fetchAndSaveOutages();

        ArgumentCaptor<EnelOutage> captor = ArgumentCaptor.forClass(EnelOutage.class);
        verify(repository).upsert(captor.capture());
        assertThat(captor.getValue().getDistrictName()).isEqualTo("Zona no identificada");
    }

    @Test
    void shouldFallbackToUnknownDistrictForZeroCoordinates() {
        EnelApiResponse.Feature feature = feature("123", "10/07/2026 08:30", 0.0, 0.0, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(List.of(new EnelApiFeatureWithEvidence(feature, "http://source", "{}")));

        scheduler.fetchAndSaveOutages();

        ArgumentCaptor<EnelOutage> captor = ArgumentCaptor.forClass(EnelOutage.class);
        verify(repository).upsert(captor.capture());
        assertThat(captor.getValue().getDistrictName()).isEqualTo("Zona no identificada");
    }

    private EnelApiResponse.Feature feature(String objectId, String interruptionDate,
                                            double lat, double lon, String serviceType) {
        EnelApiResponse.Feature feature = new EnelApiResponse.Feature();
        EnelApiResponse.Attributes attr = new EnelApiResponse.Attributes();
        attr.setObjectId(objectId);
        attr.setLatitude(lat);
        attr.setLongitude(lon);
        attr.setInterruptionDate(interruptionDate);
        attr.setServiceType(serviceType);
        feature.setAttributes(attr);
        return feature;
    }
}
