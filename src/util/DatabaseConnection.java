package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection {
    private DatabaseConnection() {
        // Utility class: no instances are needed.
    }

    public static Connection getConnection() throws SQLException {
        String url = requireEnvironmentVariable("DB_URL");
        String user = requireEnvironmentVariable("DB_USER");
        String password = requireEnvironmentVariable("DB_PASSWORD");
        return DriverManager.getConnection(url, user, password);
    }

    private static String requireEnvironmentVariable(String name) throws SQLException {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new SQLException("Missing environment variable: " + name);
        }
        return value;
    }
}
