package com.pocasluces.backend.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves exactly which rows {@code V8__merge_start_corrected_duplicates.sql} merges and which
 * it leaves alone, against a real PostgreSQL with the schema as production has it before V8
 * (V2 shape created here, V3 to V7 applied by Flyway, rows seeded in the V7 shape, then V8).
 * Needs Docker (Testcontainers): runs in CI.
 *
 * <p>Seeded scenarios (all rows at the same latitude/longitude/service type unless noted):</p>
 * <ul>
 *   <li><b>Polígono Sur</b>: A (start 23:00) seen once at 23:05:09 and resolved; B (start 22:50)
 *       first seen 23:10:09. Strict signature: merged, A into B.</li>
 *   <li><b>Chain</b>: C (start 10:00, seen 10:05, resolved) replaced by D (start 09:55, first seen
 *       10:10, seen until 10:15, resolved), replaced by E (start 09:50, first seen 10:20). Both
 *       links merge, head first: C into D, then D into E.</li>
 *   <li><b>Re-fault after a gap</b>: F resolved at 12:00; G (start 13:50) first seen 14:00. Two
 *       hours apart: a genuine new outage, not merged.</li>
 *   <li><b>Started after the last sighting</b>: H resolved at 15:05; I first seen 15:10 but
 *       started 15:07, after H was last published: not merged.</li>
 *   <li><b>Ambiguous</b>: J and K both resolved at 16:05 (two active rows at one point); L first
 *       seen 16:10 with an earlier start. Two candidates for one B: nothing merged.</li>
 *   <li><b>Different service type</b>: M resolved at 17:05 (service BT); N first seen 17:10 with
 *       an earlier start but service AT: not merged.</li>
 *   <li><b>A still active</b>: O active, fetched 18:05; P first seen 18:10 with an earlier start.
 *       A did not vanish: not merged.</li>
 * </ul>
 */
@Testcontainers
class StartCorrectionMergeMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("pocas_luces_merge_migration_test")
        .withUsername("test")
        .withPassword("test");

    private static final double LAT = 37.3521;
    private static final double LON = -5.9712;

    @BeforeEach
    void createV7StateSchemaWithPreExistingData() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS enel_outage_merged");
            statement.execute("DROP TABLE IF EXISTS telegram_weekly_summary");
            statement.execute("DROP TABLE IF EXISTS enel_outages");
            statement.execute("DROP TABLE IF EXISTS flyway_schema_history");
            // Schema as it stood after V2 (the production baseline), then V3..V7 through Flyway.
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
        }
        MigrateResult toV7 = flyway().target("7").load().migrate();
        assertThat(toV7.targetSchemaVersion).isEqualTo("7");

        try (Connection connection = connect()) {
            // Polígono Sur (audit example): 9 clients, A seen once, B republished with 22:50.
            insert(connection, "A", LAT, LON, "BT", "2026-09-30 23:00:00", "2026-09-30 23:05:09", "2026-09-30 23:05:09", true, 9);
            insert(connection, "B", LAT, LON, "BT", "2026-09-30 22:50:00", "2026-09-30 23:10:09", "2026-09-30 23:40:09", true, 9);

            // Chain: C -> D -> E at a second point.
            insert(connection, "C", LAT + 1, LON, "BT", "2026-09-20 10:00:00", "2026-09-20 10:05:00", "2026-09-20 10:05:00", true, 30);
            insert(connection, "D", LAT + 1, LON, "BT", "2026-09-20 09:55:00", "2026-09-20 10:10:00", "2026-09-20 10:15:00", true, 30);
            insert(connection, "E", LAT + 1, LON, "BT", "2026-09-20 09:50:00", "2026-09-20 10:20:00", "2026-09-20 10:30:00", true, 31);

            // Genuine re-fault after a two-hour gap.
            insert(connection, "F", LAT + 2, LON, "BT", "2026-09-21 11:00:00", "2026-09-21 11:05:00", "2026-09-21 12:00:00", true, 5);
            insert(connection, "G", LAT + 2, LON, "BT", "2026-09-21 13:50:00", "2026-09-21 14:00:00", "2026-09-21 14:30:00", true, 5);

            // Next poll, but started after the previous row was last published.
            insert(connection, "H", LAT + 3, LON, "BT", "2026-09-22 15:00:00", "2026-09-22 15:05:00", "2026-09-22 15:05:00", true, 8);
            insert(connection, "I", LAT + 3, LON, "BT", "2026-09-22 15:07:00", "2026-09-22 15:10:00", "2026-09-22 15:20:00", true, 8);

            // Ambiguous: two rows vanished together, one appeared.
            insert(connection, "J", LAT + 4, LON, "BT", "2026-09-23 16:00:00", "2026-09-23 16:05:00", "2026-09-23 16:05:00", true, 2);
            insert(connection, "K", LAT + 4, LON, "BT", "2026-09-23 16:01:00", "2026-09-23 16:05:00", "2026-09-23 16:05:00", true, 3);
            insert(connection, "L", LAT + 4, LON, "BT", "2026-09-23 15:50:00", "2026-09-23 16:10:00", "2026-09-23 16:20:00", true, 5);

            // Different service type.
            insert(connection, "M", LAT + 5, LON, "BT", "2026-09-24 17:00:00", "2026-09-24 17:05:00", "2026-09-24 17:05:00", true, 4);
            insert(connection, "N", LAT + 5, LON, "AT", "2026-09-24 16:50:00", "2026-09-24 17:10:00", "2026-09-24 17:20:00", true, 4);

            // The earlier row never vanished (still active).
            insert(connection, "O", LAT + 6, LON, "BT", "2026-09-25 18:00:00", "2026-09-25 18:05:00", "2026-09-25 18:05:00", false, 6);
            insert(connection, "P", LAT + 6, LON, "BT", "2026-09-25 17:50:00", "2026-09-25 18:10:00", "2026-09-25 18:10:00", false, 6);

            // Announcement state: A was announced as new; B never was.
            try (Statement statement = connection.createStatement()) {
                statement.execute("UPDATE enel_outages SET announce_eligible = TRUE, announced_at = '2026-09-30 23:05:09' WHERE object_id = 'A'");
                statement.execute("UPDATE enel_outages SET announce_eligible = TRUE WHERE object_id = 'B'");
            }
        }
    }

    @Test
    void shouldMergeOnlyStrictSignaturePairsAndKeepAnAuditTrail() throws SQLException {
        MigrateResult result = flyway().load().migrate();
        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(result.targetSchemaVersion).isEqualTo("8");

        try (Connection connection = connect()) {
            // Exactly A, C and D were merged away; every other row survived untouched.
            assertThat(objectIds(connection, "enel_outages"))
                .containsExactlyInAnyOrder("B", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P");
            assertThat(objectIds(connection, "enel_outage_merged")).containsExactlyInAnyOrder("A", "C", "D");

            // Polígono Sur: one outage, corrected start, first seen when A was, not brief, and
            // the original start kept for audit; A's announcement carried over so nothing is
            // announced again.
            try (ResultSet b = row(connection, "B")) {
                assertThat(b.getString("interruption_date")).startsWith("2026-09-30 22:50:00");
                assertThat(b.getString("original_interruption_date")).startsWith("2026-09-30 23:00:00");
                assertThat(b.getString("start_corrected_at")).startsWith("2026-09-30 23:10:09");
                assertThat(b.getString("first_seen_at")).startsWith("2026-09-30 23:05:09");
                assertThat(b.getString("fetched_at")).startsWith("2026-09-30 23:40:09");
                assertThat(b.getString("resolved_at")).startsWith("2026-09-30 23:40:09");
                assertThat(b.getBoolean("active")).isFalse();
                assertThat(b.getString("announced_at")).startsWith("2026-09-30 23:05:09");
                assertThat(b.getString("restoration_announced_at")).isNull();
                assertThat(b.getBoolean("announce_eligible")).isTrue();
            }
            // Audit row: A verbatim, pointing at B.
            try (PreparedStatement ps = connection.prepareStatement(
                "SELECT m.interruption_date, m.first_seen_at, m.affected_clients, m.merge_reason, b.object_id AS into_object_id " +
                    "FROM enel_outage_merged m JOIN enel_outages b ON b.id = m.merged_into_id WHERE m.object_id = 'A'");
                 ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("interruption_date")).startsWith("2026-09-30 23:00:00");
                assertThat(rs.getString("first_seen_at")).startsWith("2026-09-30 23:05:09");
                assertThat(rs.getInt("affected_clients")).isEqualTo(9);
                assertThat(rs.getString("merge_reason")).isEqualTo("start_corrected_duplicate_v8");
                assertThat(rs.getString("into_object_id")).isEqualTo("B");
            }

            // Chain: E survives with C's first sighting and C's start as the original one; the
            // audit table links C -> D (D's id, now itself in the audit table) and D -> E.
            try (ResultSet e = row(connection, "E")) {
                assertThat(e.getString("interruption_date")).startsWith("2026-09-20 09:50:00");
                assertThat(e.getString("original_interruption_date")).startsWith("2026-09-20 10:00:00");
                assertThat(e.getString("first_seen_at")).startsWith("2026-09-20 10:05:00");
            }
            try (PreparedStatement ps = connection.prepareStatement(
                "SELECT m.object_id, m.merged_into_id FROM enel_outage_merged m WHERE m.object_id IN ('C', 'D') ORDER BY m.object_id");
                 ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                long cInto = rs.getLong("merged_into_id");
                assertThat(rs.next()).isTrue();
                long dInto = rs.getLong("merged_into_id");
                assertThat(cInto).isEqualTo(idOf(connection, "enel_outage_merged", "D"));
                assertThat(dInto).isEqualTo(idOf(connection, "enel_outages", "E"));
            }

            // Untouched survivors keep their own first sighting and no correction mark.
            for (String objectId : List.of("G", "I", "L", "N", "P")) {
                try (ResultSet rs = row(connection, objectId)) {
                    assertThat(rs.getString("original_interruption_date")).as(objectId).isNull();
                    assertThat(rs.getString("start_corrected_at")).as(objectId).isNull();
                }
            }
        }
    }

    private FluentConfiguration flyway() {
        return Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .baselineVersion("2")
            .baselineOnMigrate(true)
            .locations("classpath:db/migration");
    }

    private void insert(Connection connection, String objectId, double lat, double lon, String serviceType,
                        String start, String firstSeen, String fetched, boolean resolved, int clients) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO enel_outages (
                    object_id, latitude, longitude, affected_clients, service_type, interruption_date,
                    neighborhood_name, district_name, first_seen_at, fetched_at, created_at, updated_at,
                    active, resolved_at, announce_eligible
                ) VALUES (?, ?, ?, ?, ?, ?::timestamp, 'Polígono Sur', 'Sur', ?::timestamp, ?::timestamp, ?::timestamp, ?::timestamp,
                          ?, ?::timestamp, FALSE)
                """)) {
            ps.setString(1, objectId);
            ps.setDouble(2, lat);
            ps.setDouble(3, lon);
            ps.setInt(4, clients);
            ps.setString(5, serviceType);
            ps.setString(6, start);
            ps.setString(7, firstSeen);
            ps.setString(8, fetched);
            ps.setString(9, firstSeen);
            ps.setString(10, fetched);
            ps.setBoolean(11, !resolved);
            ps.setString(12, resolved ? fetched : null);
            ps.executeUpdate();
        }
    }

    private List<String> objectIds(Connection connection, String table) throws SQLException {
        List<String> ids = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT object_id FROM " + table)) {
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
        }
        return ids;
    }

    private long idOf(Connection connection, String table, String objectId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM " + table + " WHERE object_id = ?")) {
            ps.setString(1, objectId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getLong(1);
            }
        }
    }

    /** Positioned on the single enel_outages row with this object id; the caller closes it. */
    private ResultSet row(Connection connection, String objectId) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("SELECT * FROM enel_outages WHERE object_id = ?");
        ps.setString(1, objectId);
        ResultSet rs = ps.executeQuery();
        assertThat(rs.next()).as("row %s exists", objectId).isTrue();
        return rs;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
