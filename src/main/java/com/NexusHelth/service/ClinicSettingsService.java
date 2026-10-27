package com.NexusHelth.service;

import com.NexusHelth.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class ClinicSettingsService {

    private static final double DEFAULT_APPOINTMENT_FEE = 500.0;
    private static final double MIN_FEE = 1.0;
    private static final double MAX_FEE = 1_000_000.0;

    public double getAppointmentFee() {
        String sql = "SELECT appointment_fee FROM clinic_settings WHERE id = 1";
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) {
                return AppointmentService.getAppointmentFee();
            }

            ensureDefaults(conn);

            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    double fee = rs.getDouble(1);
                    if (fee > 0) {
                        return fee;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching appointment fee: " + e.getMessage());
        }
        return AppointmentService.getAppointmentFee();
    }

    public boolean updateAppointmentFee(double newFee, int changedByUserId) {
        if (!isFeeValid(newFee)) {
            return false;
        }

        double normalizedFee = normalizeFee(newFee);

        String updateSql = "UPDATE clinic_settings SET appointment_fee = ? WHERE id = 1";
        String insertAuditSql = "INSERT INTO audit_logs (user_id, action, target_table, target_id) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) {
                return false;
            }

            ensureDefaults(conn);

            conn.setAutoCommit(false);
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                 PreparedStatement auditStmt = conn.prepareStatement(insertAuditSql)) {

                updateStmt.setDouble(1, normalizedFee);
                int updated = updateStmt.executeUpdate();

                auditStmt.setInt(1, changedByUserId);
                auditStmt.setString(2, "UPDATE_APPOINTMENT_FEE:" + normalizedFee);
                auditStmt.setString(3, "clinic_settings");
                auditStmt.setInt(4, 1);
                auditStmt.executeUpdate();

                conn.commit();

                AppointmentService.setAppointmentFee(normalizedFee);
                return updated > 0;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            System.err.println("Error updating appointment fee: " + e.getMessage());
        }

        return false;
    }

    public boolean isFeeValid(double fee) {
        return !Double.isNaN(fee) && !Double.isInfinite(fee) && fee >= MIN_FEE && fee <= MAX_FEE;
    }

    private double normalizeFee(double fee) {
        return Math.round(fee * 100.0) / 100.0;
    }

    private void ensureDefaults(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM clinic_settings WHERE id = 1")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.executeUpdate("INSERT INTO clinic_settings (id, appointment_fee) VALUES (1, " + DEFAULT_APPOINTMENT_FEE + ")");
                }
            }
            stmt.executeUpdate("UPDATE clinic_settings SET appointment_fee = " + DEFAULT_APPOINTMENT_FEE + " WHERE id = 1 AND (appointment_fee IS NULL OR appointment_fee <= 0)");
        } catch (Exception ignored) {
            // DatabaseInitializer also ensures this; keep this as a safety net.
        }
    }
}
