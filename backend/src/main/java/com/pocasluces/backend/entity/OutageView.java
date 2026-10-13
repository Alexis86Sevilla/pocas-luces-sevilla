package com.pocasluces.backend.entity;

import java.time.LocalDateTime;

/**
 * Read model of an {@link EnelOutage}: every column the public endpoints and CSV exports
 * need, and nothing else. Spring Data treats it as a closed interface projection, so a
 * repository method returning {@code OutageView} selects exactly these columns and leaves
 * {@code raw_response} (Endesa's stored feed fragment, the widest column of the table) in
 * the database. Code that already holds an entity (the native {@code /live} read, tests)
 * adapts it with {@link #of(EnelOutage)}.
 *
 * <p>Getter names must match the entity's property names: that is how the projection is
 * bound. {@link #isBrief()} is a default method and therefore not part of the projection.
 * {@link EnelOutage} must <em>not</em> implement this interface: Spring Data treats a return
 * type the entity is assignable to as "not a projection" and would load full rows again.</p>
 */
public interface OutageView {

    /** The same values as the entity, as a read model. */
    static OutageView of(EnelOutage o) {
        return new Snapshot(o.getId(), o.getObjectId(), o.getNeighborhoodName(), o.getDistrictName(), o.getServiceType(),
            o.getCause(), o.getInterruptionDate(), o.getRepositionDate(), o.getAffectedClients(), o.getLatitude(),
            o.getLongitude(), o.getSourceUrl(), o.getRawResponseHash(), o.getFirstSeenAt(), o.getFetchedAt(),
            o.getResolvedAt(), o.isActive());
    }

    /** Plain implementation backing {@link #of(EnelOutage)}. */
    record Snapshot(Long id, String objectId, String neighborhoodName, String districtName, String serviceType,
                    String cause, LocalDateTime interruptionDate, LocalDateTime repositionDate, Integer affectedClients,
                    Double latitude, Double longitude, String sourceUrl, String rawResponseHash, LocalDateTime firstSeenAt,
                    LocalDateTime fetchedAt, LocalDateTime resolvedAt, boolean active) implements OutageView {
        @Override public Long getId() { return id; }
        @Override public String getObjectId() { return objectId; }
        @Override public String getNeighborhoodName() { return neighborhoodName; }
        @Override public String getDistrictName() { return districtName; }
        @Override public String getServiceType() { return serviceType; }
        @Override public String getCause() { return cause; }
        @Override public LocalDateTime getInterruptionDate() { return interruptionDate; }
        @Override public LocalDateTime getRepositionDate() { return repositionDate; }
        @Override public Integer getAffectedClients() { return affectedClients; }
        @Override public Double getLatitude() { return latitude; }
        @Override public Double getLongitude() { return longitude; }
        @Override public String getSourceUrl() { return sourceUrl; }
        @Override public String getRawResponseHash() { return rawResponseHash; }
        @Override public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
        @Override public LocalDateTime getFetchedAt() { return fetchedAt; }
        @Override public LocalDateTime getResolvedAt() { return resolvedAt; }
        @Override public boolean isActive() { return active; }
    }

    Long getId();

    String getObjectId();

    String getNeighborhoodName();

    String getDistrictName();

    String getServiceType();

    String getCause();

    LocalDateTime getInterruptionDate();

    LocalDateTime getRepositionDate();

    Integer getAffectedClients();

    Double getLatitude();

    Double getLongitude();

    String getSourceUrl();

    String getRawResponseHash();

    LocalDateTime getFirstSeenAt();

    LocalDateTime getFetchedAt();

    LocalDateTime getResolvedAt();

    boolean isActive();

    /**
     * True for an outage that was published in exactly one poll and was already gone in the
     * next one: resolved, and its last sighting is also its first. An active outage seen
     * once is never brief, since it may still be ongoing. Derived, not stored.
     */
    default boolean isBrief() {
        return getResolvedAt() != null && getFetchedAt() != null && getFetchedAt().equals(getFirstSeenAt());
    }
}
