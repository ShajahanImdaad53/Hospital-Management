package com.NexusHelth.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;

/**
 * Seeds rich, realistic demonstration data across all user roles,
 * doctors, patients, pharmacy stock, and appointments.
 * Runs on startup if demo users do not already exist.
 */
@Component
public class DemoDataInitializer {

    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public static void initializeDemoData() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) {
                return;
            }

            // Check if demo doctor already exists
            if (userExists(conn, "doctor@clinic.com")) {
                return;
            }

            System.out.println("\n" + "=".repeat(60));
            System.out.println("🌱 SEEDING DEMONSTRATION DATA ACROSS ALL ROLES");
            System.out.println("=".repeat(60));

            // 1. Seed Staff & Patient User Accounts
            int drSarahUserId = createUser(conn, "Dr. Sarah Jenkins", "doctor@clinic.com", "Doctor@123", "doctor", "+1 (555) 234-5678");
            int drChangUserId = createUser(conn, "Dr. Michael Chang", "chang@clinic.com", "Doctor@123", "doctor", "+1 (555) 345-6789");
            int recUserId = createUser(conn, "Emily Watson", "receptionist@clinic.com", "Receptionist@123", "receptionist", "+1 (555) 456-7890");
            int pharmUserId = createUser(conn, "James Miller", "pharmacist@clinic.com", "Pharmacist@123", "pharmacist", "+1 (555) 567-8901");
            int patUserId = createUser(conn, "John Doe", "patient@clinic.com", "Patient@123", "patient", "+1 (555) 678-9012");

            // 2. Seed Doctor Profiles
            int drSarahId = createDoctor(conn, drSarahUserId, "LIC-CARD-001", "+1 (555) 234-5678", "Room 302", "Cardiology", 30, "09:00", "17:00", 12, 4.9);
            int drChangId = createDoctor(conn, drChangUserId, "LIC-NEUR-002", "+1 (555) 345-6789", "Room 205", "Neurology", 30, "08:30", "16:30", 9, 4.8);

            // 3. Seed Patient Profile
            int patId = createPatient(conn, patUserId, "PT-10001", "+1 (555) 678-9012", "1992-04-12", "male", "O+", 178.0, 74.0, 72, "45 Elm Street, Springfield");

            // 4. Seed Pharmacy Medicines
            seedMedicines(conn);

            // 5. Seed Sample Appointments
            seedAppointments(conn, patId, drSarahId, drChangId);

            System.out.println("✅ Demo data successfully loaded!");
            System.out.println("=".repeat(60) + "\n");

        } catch (Exception e) {
            System.err.println("⚠️ Warning during demo data seeding: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean userExists(Connection conn, String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking user existence: " + e.getMessage());
        }
        return false;
    }

    private static int createUser(Connection conn, String name, String email, String password, String role, String phone) throws Exception {
        String hash = passwordEncoder.encode(password);
        String sql = "INSERT INTO users (full_name, email, password_hash, role, status, phone) VALUES (?, ?, ?, ?, 'active', ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, email);
            pstmt.setString(3, hash);
            pstmt.setString(4, role);
            pstmt.setString(5, phone);
            pstmt.executeUpdate();
        }

        String idQuery = DatabaseConnection.isPostgres() ? "SELECT lastval()" : "SELECT last_insert_rowid()";
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(idQuery)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private static int createDoctor(Connection conn, int userId, String license, String phone, String room,
                                    String specialization, int duration, String start, String end,
                                    int experience, double rating) throws Exception {
        String sql = "INSERT INTO doctors (user_id, license_number, phone, assigned_room, specialization, " +
                "consultation_duration_min, working_hours_start, working_hours_end, years_experience, rating, availability_status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'available')";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setString(2, license);
            pstmt.setString(3, phone);
            pstmt.setString(4, room);
            pstmt.setString(5, specialization);
            pstmt.setInt(6, duration);
            pstmt.setString(7, start);
            pstmt.setString(8, end);
            pstmt.setInt(9, experience);
            pstmt.setDouble(10, rating);
            pstmt.executeUpdate();
        }

        String idQuery = DatabaseConnection.isPostgres() ? "SELECT lastval()" : "SELECT last_insert_rowid()";
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(idQuery)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private static int createPatient(Connection conn, int userId, String code, String phone, String dob,
                                     String gender, String blood, double height, double weight,
                                     int heartRate, String address) throws Exception {
        String sql = "INSERT INTO patients (user_id, patient_code, phone, date_of_birth, gender, blood_type, " +
                "height, weight, heart_rate, address, account_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'active')";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setString(2, code);
            pstmt.setString(3, phone);
            pstmt.setString(4, dob);
            pstmt.setString(5, gender);
            pstmt.setString(6, blood);
            pstmt.setDouble(7, height);
            pstmt.setDouble(8, weight);
            pstmt.setInt(9, heartRate);
            pstmt.setString(10, address);
            pstmt.executeUpdate();
        }

        String idQuery = DatabaseConnection.isPostgres() ? "SELECT lastval()" : "SELECT last_insert_rowid()";
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(idQuery)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private static void seedMedicines(Connection conn) {
        String countSql = "SELECT COUNT(*) FROM medicines";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        } catch (Exception ignored) {
        }

        String sql = "INSERT INTO medicines (name, batch_number, category, unit_price, stock_level, expiry_date, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        Object[][] meds = {
                {"Amoxicillin 500mg", "AMX-2026A", "Antibiotics", 15.00, 150, "2027-12-31", "in_stock"},
                {"Paracetamol 650mg", "PCM-2026B", "Analgesics", 5.00, 320, "2028-06-30", "in_stock"},
                {"Ibuprofen 400mg", "IBU-2026C", "Anti-inflammatory", 8.50, 85, "2027-09-15", "in_stock"},
                {"Metformin 500mg", "MET-2026D", "Antidiabetic", 12.00, 110, "2028-01-20", "in_stock"},
                {"Atorvastatin 20mg", "ATV-2026E", "Cardiovascular", 22.50, 95, "2027-11-10", "in_stock"},
                {"Omeprazole 20mg", "OMP-2026F", "Gastrointestinal", 14.00, 140, "2028-03-25", "in_stock"},
                {"Cetirizine 10mg", "CTZ-2026G", "Antihistamine", 6.00, 200, "2027-08-30", "in_stock"},
                {"Azithromycin 250mg", "AZM-2026H", "Antibiotics", 28.00, 60, "2027-10-15", "in_stock"}
        };

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Object[] med : meds) {
                pstmt.setString(1, (String) med[0]);
                pstmt.setString(2, (String) med[1]);
                pstmt.setString(3, (String) med[2]);
                pstmt.setDouble(4, (Double) med[3]);
                pstmt.setInt(5, (Integer) med[4]);
                pstmt.setString(6, (String) med[5]);
                pstmt.setString(7, (String) med[6]);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error seeding medicines: " + e.getMessage());
        }
    }

    private static void seedAppointments(Connection conn, int patientId, int doctorId1, int doctorId2) {
        if (patientId <= 0 || doctorId1 <= 0) {
            return;
        }

        String today = LocalDate.now().toString();
        String tomorrow = LocalDate.now().plusDays(1).toString();

        String sql = "INSERT INTO appointments (patient_id, doctor_id, appointment_date, appointment_time, status, reason, consent_accepted) " +
                "VALUES (?, ?, ?, ?, ?, ?, 1)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // Appointment 1: Today with Dr. Sarah Jenkins
            pstmt.setInt(1, patientId);
            pstmt.setInt(2, doctorId1);
            pstmt.setString(3, today);
            pstmt.setString(4, "10:00");
            pstmt.setString(5, "confirmed");
            pstmt.setString(6, "Routine cardiovascular follow-up");
            pstmt.executeUpdate();

            // Appointment 2: Tomorrow with Dr. Michael Chang (if available)
            if (doctorId2 > 0) {
                pstmt.setInt(1, patientId);
                pstmt.setInt(2, doctorId2);
                pstmt.setString(3, tomorrow);
                pstmt.setString(4, "14:30");
                pstmt.setString(5, "pending");
                pstmt.setString(6, "General neurological consultation");
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Error seeding appointments: " + e.getMessage());
        }
    }
}
