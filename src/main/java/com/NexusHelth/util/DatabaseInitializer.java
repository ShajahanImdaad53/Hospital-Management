package com.NexusHelth.util;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.NexusHelth.service.AppointmentService;

@Component
public class DatabaseInitializer implements CommandLineRunner {
    
    @Override
    public void run(String... args) throws Exception {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // Remove deprecated tables from existing databases
            try {
                stmt.execute("DROP TABLE IF EXISTS specializations");
            } catch (Exception e) {
                System.err.println("⚠️  Could not drop deprecated table 'specializations': " + e.getMessage());
            }
            
            // Read schema file from classpath (schema-postgres.sql for PostgreSQL, schema.sql for SQLite)
            String schemaFile = DatabaseConnection.isPostgres() ? "schema-postgres.sql" : "schema.sql";
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(schemaFile);
            
            if (inputStream == null) {
                System.err.println("❌ Error: " + schemaFile + " not found in classpath");
                return;
            }
            
            String schema = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            
            // Execute schema
            String[] statements = schema.split(";");
            for (String sql : statements) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        stmt.execute(trimmed);
                    } catch (Exception e) {
                        // Ignore "table already exists" errors
                        if (!e.getMessage().contains("already exists")) {
                            System.err.println("⚠️  SQL Error: " + e.getMessage());
                        }
                    }
                }
            }
            
            System.out.println("✅ Database schema initialized successfully");

            // Ensure new settings columns exist for existing databases
            ensureClinicSettingsFeeColumn(conn);
            ensureDefaultAppointmentFee(conn);
            ensureAppointmentsBookingColumns(conn);
            ensureDoctorsAvailabilityColumn(conn);

            // Ensure prescription-linked billing support exists
            ensureInvoicesPrescriptionIdColumn(conn);
            ensureInvoicesPrescriptionIdIndex(conn);

            // Hydrate runtime fee cache from DB
            double persistedFee = loadAppointmentFee(conn);
            if (persistedFee > 0) {
                AppointmentService.setAppointmentFee(persistedFee);
                System.out.println("✅ Appointment fee loaded from DB: " + persistedFee);
            }
            
            // Initialize default admin account
            AdminInitializer.initializeAdminAccount();

            // Initialize comprehensive demonstration data across all user roles
            DemoDataInitializer.initializeDemoData();

            // Backfill missing transactions for already-paid invoices
            backfillMissingTransactions(conn);
        } catch (Exception e) {
            System.err.println("❌ Error initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void ensureInvoicesPrescriptionIdColumn(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE invoices ADD COLUMN prescription_id INTEGER");
            System.out.println("✅ Migrated invoices: added prescription_id column");
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("duplicate column") || msg.contains("already exists")) {
                return;
            }
            System.err.println("⚠️  invoices migration warning: " + e.getMessage());
        }
    }

    private void ensureInvoicesPrescriptionIdIndex(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_invoices_prescription_id ON invoices(prescription_id)");
        } catch (Exception e) {
            // Avoid noisy startup logs on older DBs; index is only an optimization.
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("no such column") || msg.contains("duplicate") || msg.contains("already exists")) {
                return;
            }
            System.err.println("⚠️  invoices index warning: " + e.getMessage());
        }
    }

    private void ensureClinicSettingsFeeColumn(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE clinic_settings ADD COLUMN appointment_fee REAL NOT NULL DEFAULT 500.0");
            System.out.println("✅ Migrated clinic_settings: added appointment_fee column");
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("duplicate column") || msg.contains("already exists")) {
                // Column is already present; ignore
                return;
            }
            System.err.println("⚠️  clinic_settings migration warning: " + e.getMessage());
        }
    }

    private void ensureDefaultAppointmentFee(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            // Ensure row exists in a database-agnostic way
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM clinic_settings WHERE id = 1")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.executeUpdate("INSERT INTO clinic_settings (id, appointment_fee) VALUES (1, 500.0)");
                }
            }
            // Ensure value is valid
            stmt.executeUpdate("UPDATE clinic_settings SET appointment_fee = 500.0 WHERE id = 1 AND (appointment_fee IS NULL OR appointment_fee <= 0)");
        } catch (Exception e) {
            System.err.println("⚠️  Could not ensure default appointment fee: " + e.getMessage());
        }
    }

    private void ensureAppointmentsBookingColumns(Connection conn) {
        ensureColumn(conn, "ALTER TABLE appointments ADD COLUMN preferred_language TEXT");
        ensureColumn(conn, "ALTER TABLE appointments ADD COLUMN emergency_contact_name TEXT");
        ensureColumn(conn, "ALTER TABLE appointments ADD COLUMN emergency_contact_phone TEXT");
        ensureColumn(conn, "ALTER TABLE appointments ADD COLUMN consent_accepted INTEGER NOT NULL DEFAULT 0");
    }

    private void ensureDoctorsAvailabilityColumn(Connection conn) {
        ensureColumn(conn, "ALTER TABLE doctors ADD COLUMN availability_status TEXT NOT NULL DEFAULT 'available'");
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE doctors SET availability_status = 'available' WHERE availability_status IS NULL OR TRIM(availability_status) = ''");
        } catch (Exception e) {
            System.err.println("WARN doctor availability migration issue: " + e.getMessage());
        }
    }

    private void ensureColumn(Connection conn, String alterSql) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(alterSql);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("duplicate column") || msg.contains("already exists")) {
                return;
            }
            System.err.println("WARN migration issue: " + e.getMessage());
        }
    }

    private double loadAppointmentFee(Connection conn) {
        String sql = "SELECT appointment_fee FROM clinic_settings WHERE id = 1";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getDouble(1);
            }
        } catch (Exception e) {
            System.err.println("⚠️  Could not load appointment fee: " + e.getMessage());
        }
        return AppointmentService.getAppointmentFee();
    }

    private void backfillMissingTransactions(Connection conn) {
        System.out.println("🔄 Backfilling missing transactions for paid invoices...");

        String backfillSql = "INSERT INTO transactions (invoice_id, transaction_code, type, department, amount, status, transacted_at) " +
                "SELECT i.id, " +
                "       'TXN-' || i.invoice_number, " +
                "       COALESCE(i.consultation_type, 'Payment'), " +
                "       COALESCE(d.specialization, 'N/A'), " +
                "       COALESCE(i.total_amount, 0.0), " +
                "       'settled', " +
                "       COALESCE(i.paid_at, i.created_at, CURRENT_TIMESTAMP) " +
                "FROM invoices i " +
                "LEFT JOIN doctors d ON i.doctor_id = d.id " +
                "WHERE i.status = 'paid' " +
                "  AND NOT EXISTS (SELECT 1 FROM transactions t WHERE t.invoice_id = i.id)";

        try (Statement stmt = conn.createStatement()) {
            int inserted = stmt.executeUpdate(backfillSql);
            System.out.println("✅ Backfill complete. Inserted transactions: " + inserted);
        } catch (Exception e) {
            System.err.println("⚠️ Backfill failed: " + e.getMessage());
        }
    }
}

