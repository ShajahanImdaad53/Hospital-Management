package com.NexusHelth.util;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import com.NexusHelth.config.DatabaseConfig;

public class DatabaseConnection {

    private static String cachedJdbcUrl = null;
    private static String cachedUser = null;
    private static String cachedPassword = null;
    private static boolean isPostgres = false;
    private static boolean initialized = false;

    private static synchronized void initialize() {
        if (initialized) {
            return;
        }

        // Check for cloud database URL (DATABASE_URL, POSTGRES_URL, or SPRING_DATASOURCE_URL)
        String rawDbUrl = System.getenv("DATABASE_URL");
        if (rawDbUrl == null || rawDbUrl.trim().isEmpty()) {
            rawDbUrl = System.getenv("POSTGRES_URL");
        }
        if (rawDbUrl == null || rawDbUrl.trim().isEmpty()) {
            rawDbUrl = System.getenv("SPRING_DATASOURCE_URL");
        }
        if (rawDbUrl == null || rawDbUrl.trim().isEmpty()) {
            rawDbUrl = System.getProperty("DATABASE_URL");
        }

        if (rawDbUrl != null && !rawDbUrl.trim().isEmpty() &&
                (rawDbUrl.startsWith("postgres://") || rawDbUrl.startsWith("postgresql://") || rawDbUrl.startsWith("jdbc:postgresql:"))) {
            try {
                Class.forName("org.postgresql.Driver");
                isPostgres = true;

                if (rawDbUrl.startsWith("jdbc:postgresql:")) {
                    cachedJdbcUrl = rawDbUrl;
                    cachedUser = System.getenv("SPRING_DATASOURCE_USERNAME");
                    cachedPassword = System.getenv("SPRING_DATASOURCE_PASSWORD");
                } else {
                    // Standard postgres://user:password@host:port/dbname format from Supabase/Neon/Render
                    URI dbUri = new URI(rawDbUrl.replace("postgresql://", "http://").replace("postgres://", "http://"));
                    String userInfo = dbUri.getUserInfo();
                    if (userInfo != null && userInfo.contains(":")) {
                        String[] parts = userInfo.split(":", 2);
                        cachedUser = parts[0];
                        cachedPassword = parts[1];
                    }

                    int port = dbUri.getPort() > 0 ? dbUri.getPort() : 5432;
                    String host = dbUri.getHost();
                    String path = dbUri.getPath(); // "/dbname"
                    String query = dbUri.getQuery();

                    cachedJdbcUrl = "jdbc:postgresql://" + host + ":" + port + path + (query != null ? "?" + query : "?sslmode=require");
                }
                System.out.println("🐘 Connected to Cloud PostgreSQL database: " + cachedJdbcUrl.replaceAll(":[^:@]+@", ":***@"));
            } catch (Exception e) {
                System.err.println("⚠️ Could not parse DATABASE_URL, falling back to SQLite: " + e.getMessage());
                isPostgres = false;
            }
        }

        if (!isPostgres) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                System.err.println("❌ SQLite JDBC Driver not found: " + e.getMessage());
            }
            cachedJdbcUrl = "jdbc:sqlite:" + DatabaseConfig.getDatabasePath();
            System.out.println("💾 Using SQLite database at: " + cachedJdbcUrl);
        }

        initialized = true;
    }

    public static boolean isPostgres() {
        if (!initialized) {
            initialize();
        }
        return isPostgres;
    }

    public static Connection getConnection() {
        if (!initialized) {
            initialize();
        }

        try {
            Connection conn;
            if (isPostgres) {
                if (cachedUser != null && cachedPassword != null) {
                    conn = DriverManager.getConnection(cachedJdbcUrl, cachedUser, cachedPassword);
                } else {
                    conn = DriverManager.getConnection(cachedJdbcUrl);
                }
            } else {
                conn = DriverManager.getConnection(cachedJdbcUrl);
                conn.setAutoCommit(true);
            }
            return conn;
        } catch (SQLException e) {
            System.err.println("❌ DatabaseConnection Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
