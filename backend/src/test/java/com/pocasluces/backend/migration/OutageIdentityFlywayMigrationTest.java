package com.pocasluces.backend.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves that the V3 (outage identity), V4 (cause column), V5 (resolved_at column and
 * backfill), V6 (Telegram announcement state and go-live backfill) and V7 (weekly summary
 * table) Flyway migrations
 * apply cleanly to a database that already has data in the pre-migration (V2) shape — the
 * exact situation production is in, given {@code baseline-on-migrate: true}.
 *
 * <p>Unlike {@link com.pocasluces.backend.repository.EnelOutageRepositoryPostgresTest},
 * this test never boots Spring, so Hibernate's {@code ddl-auto} never runs and Flyway's
 * own SQL scripts are what create/alter the schema — the same mechanism used in the
 * {@code prod} profile ({@code flyway.enabled=true}, {@code ddl-auto=validate}).</p>
 */
@Testcontainers
class OutageIdentityFlywayMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("pocas_luces_migration_test")
        .withUsername("test")
        .withPassword("test");

    @BeforeEach
    void createV2StateSchemaWithPreExistingData() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            // Schema exactly as it stood after V1 (adds `active`) and V2 (adds
            // `district_name`), i.e. the state V3 must migrate away from.
            statement.execute("""
                CREATE TABLE enel_outages (
                    id BIGSERIAL PRIMARY KEY,
                    object_id VARCHAR(50) NOT NULL,
                    latitude DOUBLE PRECISION,
                    longitude DOUBLE PRECISION,
                    affected_clients INTEGER,
                    service_type VARCHAR(10) NOT NULL,
                    interruption_date TIMESTAMP NOT NULL,
                    reposition_date TIMESTAMP,
                    neighborhood_name VARCHAR(100) NOT NULL,
                    district_name VARCHAR(100),
                    source_url VARCHAR(500),
                    raw_response_hash VARCHAR(64),
                    raw_response TEXT,
                    first_seen_at TIMESTAMP NOT NULL,
                    fetched_at TIMESTAMP NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    active BOOLEAN NOT NULL DEFAULT true,
                    CONSTRAINT uk_enel_outage_natural_key UNIQUE (neighborhood_name, interruption_date, service_type)
                )
                """);
            statement.execute("CREATE INDEX idx_enel_outage_object_id ON enel_outages (object_id)");
            statement.execute("CREATE INDEX idx_enel_outage_interruption_date ON enel_outages (interruption_date)");
            statement.execute("CREATE INDEX idx_enel_outage_neighborhood ON enel_outages (neighborhood_name)");
            statement.execute("CREATE INDEX idx_enel_outage_district ON enel_outages (district_name)");
            statement.execute("CREATE INDEX idx_enel_outage_fetched_at ON enel_outages (fetched_at)");

            // Row 1: an ordinary outage, kept as-is.
            insertRow(connection, "San Pablo", 37.3970, -5.9800, "2026-07-10 08:30:00", "AT", "2026-07-10 08:00:00");

            // Rows 2 & 3: two rows that, under the OLD (neighborhood_name, interruption_date,
            // service_type) key, were allowed to coexist because their neighborhood_name
            // differs — but they share the exact same coordinates/start/type, which is what
            // the NEW key identifies as the same physical outage. This cannot happen from
            // real application writes (the old constraint would already have merged them,
            // since neighborhood is a pure function of the coordinates), but the migration's
            // defensive dedup must still handle it safely if it is ever found. Row 3 was
            // fetched later and must be the one that survives.
            insertRow(connection, "Nervion", 37.4100, -5.9700, "2026-07-11 09:00:00", "BT", "2026-07-11 09:00:00");
            insertRow(connection, "Triana", 37.4100, -5.9700, "2026-07-11 09:00:00", "BT", "2026-07-11 10:00:00");

            // Row 4: pre-existing row with NULL coordinates (Endesa feed omitted them),
            // which the NOT NULL backfill step must handle instead of failing the migration.
            insertNullCoordinateRow(connection, "2026-07-12 10:00:00", "GB", "2026-07-12 10:00:00");

            // Row 5: a pre-existing INACTIVE row (already resolved before V5 ever ran).
            // V5's backfill must set resolved_at = fetched_at for it, since fetched_at is
            // the only record of when it was last seen.
            insertRow(connection, "Macarena", 37.4050, -5.9900, "2026-07-13 07:00:00", "AT",
                "2026-07-13 07:20:00", false);
        }
    }

    @Test
    void shouldApplyV3ThroughV7OnPreExistingData() throws SQLException {
        Flyway flyway = Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .baselineVersion("2")
            .baselineOnMigrate(true)
            .locations("classpath:db/migration")
            .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).isEqualTo(5); // V3 to V7, none skipped
        assertThat(result.targetSchemaVersion).isEqualTo("7");

        try (Connection connection = connect()) {
            connection.setAutoCommit(true);

            // The defensive dedup removed exactly the one superseded duplicate.
            assertRowCount(connection, 4);

            // The more recently fetched of the two colliding rows survived.
            assertThat(neighborhoodFor(connection, 37.4100, -5.9700, "2026-07-11 09:00:00", "BT"))
                .isEqualTo("Triana");

            // NULL coordinates were backfilled to 0.0 instead of blocking the migration.
            assertThat(latitudeLongitudeFor(connection, "2026-07-12 10:00:00", "GB"))
                .containsExactly(0.0, 0.0);

            // Old constraint gone, new constraint enforced.
            assertThat(constraintExists(connection, "uk_enel_outage_natural_key")).isFalse();
            assertThat(constraintExists(connection, "uk_enel_outage_location_key")).isTrue();

            assertThatThrownBy(() -> insertRow(connection, "San Pablo", 37.3970, -5.9800,
                "2026-07-10 08:30:00", "AT", "2026-07-10 08:00:00"))
                .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> insertNullCoordinateRow(connection, "2026-07-13 10:00:00", "GB", "2026-07-13 10:00:00"))
                .isInstanceOf(SQLException.class);

            // V4's nullable `cause` column exists and round-trips a value.
            try (PreparedStatement update = connection.prepareStatement(
                "UPDATE enel_outages SET cause = ? WHERE neighborhood_name = 'San Pablo'")) {
                update.setString(1, "Avería");
                update.executeUpdate();
            }
            try (PreparedStatement select = connection.prepareStatement(
                "SELECT cause FROM enel_outages WHERE neighborhood_name = 'San Pablo'");
                 ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("cause")).isEqualTo("Avería");
            }

            // V5's nullable `resolved_at` column exists, and the backfill set it to
            // fetched_at for the pre-existing inactive row (the last time it was seen)...
            assertThat(resolvedAtFor(connection, "Macarena")).isEqualTo("2026-07-13 07:20:00");
            // ...while active rows, which have no resolution yet, were left NULL.
            assertThat(resolvedAtFor(connection, "San Pablo")).isNull();

            // V6: every pre-existing row (active or not) is excluded from the Telegram alerts
            // forever, so nothing that was already known at go-live is announced as new or as
            // restored; the marks and the missing-poll counter start empty.
            assertThat(count(connection, "announce_eligible = TRUE")).isZero();
            assertThat(count(connection, "announce_eligible = FALSE")).isEqualTo(4);
            assertThat(count(connection, "announced_at IS NULL AND restoration_announced_at IS NULL AND missing_polls = 0"))
                .isEqualTo(4);

            // ...whereas a row inserted after the migration is eligible by default.
            insertRow(connection, "Bellavista", 37.3500, -5.9700, "2026-07-14 09:00:00", "BT", "2026-07-14 09:05:00");
            assertThat(count(connection, "neighborhood_name = 'Bellavista' AND announce_eligible = TRUE " +
                "AND announced_at IS NULL AND restoration_announced_at IS NULL AND missing_polls = 0")).isEqualTo(1);
        }
    }

    private int count(Connection connection, String whereClause) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM enel_outages WHERE " + whereClause)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void insertRow(Connection connection, String neighborhood, double lat, double lon,
                            String interruptionDate, String serviceType, String fetchedAt) throws SQLException {
        insertRow(connection, neighborhood, lat, lon, interruptionDate, serviceType, fetchedAt, true);
    }

    private void insertRow(Connection connection, String neighborhood, double lat, double lon,
                            String interruptionDate, String serviceType, String fetchedAt, boolean active) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO enel_outages (
                    object_id, latitude, longitude, affected_clients, service_type,
                    interruption_date, neighborhood_name, first_seen_at, fetched_at, created_at, updated_at, active
                ) VALUES (?, ?, ?, 10, ?, ?::timestamp, ?, ?::timestamp, ?::timestamp, ?::timestamp, ?::timestamp, ?)
                """)) {
            ps.setString(1, "obj-" + neighborhood);
            ps.setDouble(2, lat);
            ps.setDouble(3, lon);
            ps.setString(4, serviceType);
            ps.setString(5, interruptionDate);
            ps.setString(6, neighborhood);
            ps.setString(7, fetchedAt);
            ps.setString(8, fetchedAt);
            ps.setString(9, fetchedAt);
            ps.setString(10, fetchedAt);
            ps.setBoolean(11, active);
            ps.executeUpdate();
        }
    }

    private void insertNullCoordinateRow(Connection connection, String interruptionDate, String serviceType,
                                          String fetchedAt) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO enel_outages (
                    object_id, latitude, longitude, affected_clients, service_type,
                    interruption_date, neighborhood_name, first_seen_at, fetched_at, created_at, updated_at, active
                ) VALUES (?, NULL, NULL, 10, ?, ?::timestamp, 'Zona no identificada', ?::timestamp, ?::timestamp, ?::timestamp, ?::timestamp, true)
                """)) {
            ps.setString(1, "obj-null-" + interruptionDate);
            ps.setString(2, serviceType);
            ps.setString(3, interruptionDate);
            ps.setString(4, fetchedAt);
            ps.setString(5, fetchedAt);
            ps.setString(6, fetchedAt);
            ps.setString(7, fetchedAt);
            ps.executeUpdate();
        }
    }

    private void assertRowCount(Connection connection, int expected) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM enel_outages")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(expected);
        }
    }

    private String neighborhoodFor(Connection connection, double lat, double lon, String interruptionDate,
                                    String serviceType) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT neighborhood_name FROM enel_outages WHERE latitude = ? AND longitude = ? " +
                "AND interruption_date = ?::timestamp AND service_type = ?")) {
            ps.setDouble(1, lat);
            ps.setDouble(2, lon);
            ps.setString(3, interruptionDate);
            ps.setString(4, serviceType);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                String neighborhood = rs.getString("neighborhood_name");
                assertThat(rs.next()).isFalse();
                return neighborhood;
            }
        }
    }

    private double[] latitudeLongitudeFor(Connection connection, String interruptionDate, String serviceType) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT latitude, longitude FROM enel_outages WHERE interruption_date = ?::timestamp AND service_type = ?")) {
            ps.setString(1, interruptionDate);
            ps.setString(2, serviceType);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return new double[] {rs.getDouble("latitude"), rs.getDouble("longitude")};
            }
        }
    }

    private String resolvedAtFor(Connection connection, String neighborhood) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT resolved_at FROM enel_outages WHERE neighborhood_name = ?")) {
            ps.setString(1, neighborhood);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                java.sql.Timestamp resolvedAt = rs.getTimestamp("resolved_at");
                return resolvedAt == null ? null : resolvedAt.toString().substring(0, 19);
            }
        }
    }

    private boolean constraintExists(Connection connection, String constraintName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = ?")) {
            ps.setString(1, constraintName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
