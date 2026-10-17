package util;

import java.nio.file.*;
import java.sql.*;

/** Transactional, versioned migrations, serialized across concurrent starts. */
public final class SchemaMigrator {
    private SchemaMigrator() {}
    public static void migrate() throws Exception {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.execute("SELECT pg_advisory_xact_lock(82431017)");
                s.execute("CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY, applied_at TIMESTAMPTZ DEFAULT now())");
                apply(c, 1, "sql/schema.sql", "sql/recruitment.sql");
                apply(c, 2, "sql/migrations/V2__tracking_and_accounts.sql");
                c.commit();
            } catch (Exception ex) { c.rollback(); throw ex; }
        }
    }
    private static void apply(Connection c, int version, String... paths) throws Exception {
        try (PreparedStatement s = c.prepareStatement("SELECT 1 FROM schema_migrations WHERE version=?")) {
            s.setInt(1, version);
            try (ResultSet r = s.executeQuery()) { if (r.next()) return; }
        }
        for (String path : paths) try (Statement s = c.createStatement()) {
            for (String sql : Files.readString(Path.of(path)).split(";")) if (!sql.isBlank()) s.execute(sql);
        }
        try (PreparedStatement s = c.prepareStatement("INSERT INTO schema_migrations(version) VALUES(?)")) {
            s.setInt(1, version); s.executeUpdate();
        }
    }
}
