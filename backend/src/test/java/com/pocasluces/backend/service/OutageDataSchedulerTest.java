package com.pocasluces.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocasluces.backend.dto.EnelApiFetchResult;
import com.pocasluces.backend.dto.EnelApiResponse;
import com.pocasluces.backend.entity.EnelOutage;
import com.pocasluces.backend.repository.EnelOutageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private ObjectMapper objectMapper;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneId.of("UTC"));
    private OutageDataScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutageDataScheduler(enelApiService, repository, locator, objectMapper, clock);
    }

    @Test
    void shouldInsertNewOutageAndSetFirstSeenAt() throws Exception {
        EnelApiResponse.Feature feature = feature(123L, "10/07/2026 08:30", 37.3970, -5.9800, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(new EnelApiFetchResult("http://source", "{}", List.of(feature)));
        when(locator.findNeighborhood(37.3970, -5.9800)).thenReturn("San Pablo");
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"raw\":\"data\"}");
        when(repository.findByObjectId(123L)).thenReturn(Optional.empty());

        scheduler.fetchAndSaveOutages();

        ArgumentCaptor<EnelOutage> captor = ArgumentCaptor.forClass(EnelOutage.class);
        verify(repository).save(captor.capture());
        EnelOutage saved = captor.getValue();

        assertThat(saved.getObjectId()).isEqualTo(123L);
        assertThat(saved.getNeighborhoodName()).isEqualTo("San Pablo");
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
    void shouldUpdateExistingOutageWithoutChangingFirstSeenAt() throws Exception {
        EnelApiResponse.Feature feature = feature(123L, "10/07/2026 08:30", 37.3970, -5.9800, "AT");
        EnelOutage existing = EnelOutage.builder()
            .id(1L)
            .objectId(123L)
            .firstSeenAt(LocalDateTime.of(2026, 7, 1, 0, 0))
            .createdAt(LocalDateTime.of(2026, 7, 1, 0, 0))
            .build();

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(new EnelApiFetchResult("http://source", "{}", List.of(feature)));
        when(locator.findNeighborhood(37.3970, -5.9800)).thenReturn("San Pablo");
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"raw\":\"data\"}");
        when(repository.findByObjectId(123L)).thenReturn(Optional.of(existing));

        scheduler.fetchAndSaveOutages();

        assertThat(existing.getFirstSeenAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(existing.getUpdatedAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(existing.getFetchedAt()).isEqualTo(LocalDateTime.now(clock));
    }

    @Test
    void shouldSkipOutageWithNullObjectId() throws Exception {
        EnelApiResponse.Feature feature = feature(null, "10/07/2026 08:30", 37.0, -5.0, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(new EnelApiFetchResult("http://source", "{}", List.of(feature)));

        scheduler.fetchAndSaveOutages();

        verify(repository, never()).save(any());
    }

    @Test
    void shouldSkipOutageWithUnparseableInterruptionDate() throws Exception {
        EnelApiResponse.Feature feature = feature(123L, "not-a-date", 37.0, -5.0, "AT");

        when(enelApiService.fetchSevillaOutages())
            .thenReturn(new EnelApiFetchResult("http://source", "{}", List.of(feature)));

        scheduler.fetchAndSaveOutages();

        verify(repository, never()).save(any());
    }

    @Test
    void shouldAbortWhenApiThrows() {
        when(enelApiService.fetchSevillaOutages())
            .thenThrow(new EnelApiService.EnelApiException("API down"));

        scheduler.fetchAndSaveOutages();

        verify(repository, never()).save(any());
    }

    private EnelApiResponse.Feature feature(Long objectId, String interruptionDate,
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
