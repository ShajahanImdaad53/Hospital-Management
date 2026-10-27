package com.NexusHelth.service;

import com.NexusHelth.model.Doctor;
import com.NexusHelth.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;

public class AppointmentService {

    // Static appointment fee - can be set from config
    private static volatile double appointmentFee = 500.0;

    // Getter and Setter for appointment fee
    public static double getAppointmentFee() {
        return appointmentFee;
    }

    public static void setAppointmentFee(double fee) {
        appointmentFee = fee;
    }

    public List<Doctor> getAvailableDoctors() {
        return getAvailableDoctors(null, null, null, null);
    }

    public List<Doctor> getAvailableDoctors(String search, String specialization, Double minRating, String availabilityStatus) {
        List<Doctor> doctors = new ArrayList<>();
        String query = "SELECT d.id, d.user_id, u.full_name, d.specialization, u.profile_picture, " +
                "d.consultation_duration_min, d.working_hours_start, d.working_hours_end, " +
                "d.years_experience, d.rating, d.availability_status " +
                "FROM doctors d " +
                "JOIN users u ON d.user_id = u.id " +
                "WHERE u.status = 'active' AND u.role = 'doctor' " +
                "AND (? IS NULL OR ? = '' OR LOWER(u.full_name) LIKE LOWER(?)) " +
                "AND (? IS NULL OR ? = '' OR LOWER(d.specialization) LIKE LOWER(?)) " +
                "AND (? IS NULL OR d.rating >= ?) " +
                "ORDER BY d.rating DESC, u.full_name ASC";

        String likeSearch = (search == null || search.trim().isEmpty()) ? null : ("%" + search.trim() + "%");
        String likeSpecialization = (specialization == null || specialization.trim().isEmpty()) ? null : ("%" + specialization.trim() + "%");
        String availability = (availabilityStatus == null) ? null : availabilityStatus.trim().toLowerCase();
        double fee = getPersistedAppointmentFee();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, search);
            pstmt.setString(2, search);
            pstmt.setString(3, likeSearch);
            pstmt.setString(4, specialization);
            pstmt.setString(5, specialization);
            pstmt.setString(6, likeSpecialization);
            if (minRating == null) {
                pstmt.setNull(7, java.sql.Types.DOUBLE);
                pstmt.setNull(8, java.sql.Types.DOUBLE);
            } else {
                pstmt.setDouble(7, minRating);
                pstmt.setDouble(8, minRating);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String start = rs.getString("working_hours_start");
                    String end = rs.getString("working_hours_end");
                    int duration = rs.getInt("consultation_duration_min");
                    String dbAvailability = rs.getString("availability_status");
                    boolean unavailableByLeave = dbAvailability != null && "unavailable".equalsIgnoreCase(dbAvailability.trim());
                    String computedStatus = unavailableByLeave ? "Unavailable" : computeAvailabilityStatus(start, end, duration);

                    // Patients should never see doctors currently on leave/unavailable.
                    if (unavailableByLeave) {
                        continue;
                    }

                    if ("available".equals(availability) && !"Available".equalsIgnoreCase(computedStatus)) {
                        continue;
                    }
                    if ("unavailable".equals(availability) && "Available".equalsIgnoreCase(computedStatus)) {
                        continue;
                    }

                    doctors.add(new Doctor(
                            rs.getInt("id"),
                            rs.getInt("user_id"),
                            rs.getString("full_name"),
                            rs.getString("specialization"),
                            rs.getString("profile_picture"),
                            duration > 0 ? duration : 30,
                            (start == null || start.trim().isEmpty()) ? "09:00" : start,
                            (end == null || end.trim().isEmpty()) ? "17:00" : end,
                            rs.getInt("years_experience"),
                            rs.getDouble("rating"),
                            computedStatus,
                            fee));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching available doctors: " + e.getMessage());
            e.printStackTrace();
        }
        return doctors;
    }

    public List<String> getBookedTimeSlots(int doctorId, String date) {
        List<String> bookedSlots = new ArrayList<>();
        String query = "SELECT appointment_time FROM appointments " +
                "WHERE doctor_id = ? AND appointment_date = ? AND status IN ('scheduled', 'confirmed')";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, doctorId);
            pstmt.setString(2, date);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String normalized = normalizeTimeValue(rs.getString("appointment_time"));
                    if (normalized != null && !normalized.isEmpty()) {
                        bookedSlots.add(normalized);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching booked time slots: " + e.getMessage());
            e.printStackTrace();
        }
        return bookedSlots;
    }

    public boolean isTimeSlotAvailable(int doctorId, String date, String time) {
        String normalizedTime = normalizeTimeValue(time);
        if (normalizedTime == null) {
            return false;
        }
        List<String> booked = getBookedTimeSlots(doctorId, date);
        return !booked.contains(normalizedTime);
    }

    public int bookAppointment(int patientId, int doctorId, String date, String time) {
        return bookAppointmentDetailed(patientId, doctorId, date, time, null, null, null, null, null, true);
    }

    public int bookAppointmentDetailed(int patientId,
                                       int doctorId,
                                       String date,
                                       String time,
                                       Integer bookedBy,
                                       String notes,
                                       String preferredLanguage,
                                       String emergencyContactName,
                                       String emergencyContactPhone,
                                       boolean consentAccepted) {
        if (!isDoctorCurrentlyAvailable(doctorId)) {
            return -1;
        }

        LocalDate appointmentDate;
        try {
            appointmentDate = LocalDate.parse(date);
        } catch (Exception e) {
            return -1;
        }

        LocalDate today = LocalDate.now();
        if (appointmentDate.isBefore(today) || appointmentDate.isAfter(today.plusMonths(2))) {
            return -1;
        }

        String normalizedTime = normalizeTimeValue(time);
        if (normalizedTime == null) {
            return -1;
        }

        if (!isTimeInsideDoctorSchedule(doctorId, normalizedTime)) {
            return -1;
        }

        // Double check availability to prevent concurrent booking issues
        if (!isTimeSlotAvailable(doctorId, date, normalizedTime)) {
            return -1;
        }

        String insertQuery = "INSERT INTO appointments (patient_id, doctor_id, booked_by, appointment_date, appointment_time, status, notes, " +
                "preferred_language, emergency_contact_name, emergency_contact_phone, consent_accepted) " +
                "VALUES (?, ?, ?, ?, ?, 'scheduled', ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement insertPstmt = conn.prepareStatement(insertQuery)) {

            insertPstmt.setInt(1, patientId);
            insertPstmt.setInt(2, doctorId);
            if (bookedBy == null || bookedBy <= 0) {
                insertPstmt.setNull(3, java.sql.Types.INTEGER);
            } else {
                insertPstmt.setInt(3, bookedBy);
            }
            insertPstmt.setString(4, date);
            insertPstmt.setString(5, normalizedTime);
            insertPstmt.setString(6, notes);
            insertPstmt.setString(7, preferredLanguage);
            insertPstmt.setString(8, emergencyContactName);
            insertPstmt.setString(9, emergencyContactPhone);
            insertPstmt.setInt(10, consentAccepted ? 1 : 0);

            int affectedRows = insertPstmt.executeUpdate();
            if (affectedRows > 0) {
                // SQLite doesn't support getGeneratedKeys(), so query the last inserted rowid
                String idQuery = "SELECT last_insert_rowid() as id";
                try (PreparedStatement idPstmt = conn.prepareStatement(idQuery);
                     ResultSet rs = idPstmt.executeQuery()) {
                    if (rs.next()) {
                        int appointmentId = rs.getInt("id");
                        System.out.println("✅ Appointment booked successfully with ID: " + appointmentId);
                        return appointmentId;
                    }
                }
            }
            return -1;
        } catch (SQLException e) {
            System.err.println("Error booking appointment: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public Doctor getDoctorById(int doctorId) {
        String query = "SELECT d.id, d.user_id, u.full_name, d.specialization, u.profile_picture, " +
                "d.consultation_duration_min, d.working_hours_start, d.working_hours_end, d.years_experience, d.rating, d.availability_status " +
                "FROM doctors d JOIN users u ON d.user_id = u.id WHERE d.id = ?";
        double fee = getPersistedAppointmentFee();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, doctorId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String start = rs.getString("working_hours_start");
                    String end = rs.getString("working_hours_end");
                    int duration = rs.getInt("consultation_duration_min");
                    String dbAvailability = rs.getString("availability_status");
                    boolean unavailableByLeave = dbAvailability != null && "unavailable".equalsIgnoreCase(dbAvailability.trim());
                    return new Doctor(
                            rs.getInt("id"),
                            rs.getInt("user_id"),
                            rs.getString("full_name"),
                            rs.getString("specialization"),
                            rs.getString("profile_picture"),
                            duration > 0 ? duration : 30,
                            (start == null || start.trim().isEmpty()) ? "09:00" : start,
                            (end == null || end.trim().isEmpty()) ? "17:00" : end,
                            rs.getInt("years_experience"),
                            rs.getDouble("rating"),
                            unavailableByLeave ? "Unavailable" : computeAvailabilityStatus(start, end, duration),
                            fee
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching doctor by id: " + e.getMessage());
        }
        return null;
    }

    public List<Map<String, Object>> getDoctorAvailabilityForRange(int doctorId, String startDate, String endDate) {
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(startDate);
            end = LocalDate.parse(endDate);
        } catch (Exception e) {
            return Collections.emptyList();
        }

        if (end.isBefore(start)) {
            return Collections.emptyList();
        }

        Doctor doctor = getDoctorById(doctorId);
        if (doctor == null || !isDoctorCurrentlyAvailable(doctorId)) {
            return Collections.emptyList();
        }

        int duration = doctor.getConsultationDurationMin() > 0 ? doctor.getConsultationDurationMin() : 30;
        List<String> dailyTemplate = generateSlots(doctor.getWorkingHoursStart(), doctor.getWorkingHoursEnd(), duration);
        if (dailyTemplate.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            String date = cursor.toString();
            List<String> booked = getBookedTimeSlots(doctorId, date);
            int totalSlots = dailyTemplate.size();
            int bookedSlots = booked.size();
            int availableSlots = Math.max(0, totalSlots - bookedSlots);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date);
            row.put("totalSlots", totalSlots);
            row.put("bookedSlots", bookedSlots);
            row.put("availableSlots", availableSlots);
            row.put("status", availableSlots > 0 ? "available" : "unavailable");
            result.add(row);

            cursor = cursor.plusDays(1);
        }

        return result;
    }

    public Map<String, Object> getClinicSettingsSummary() {
        Map<String, Object> data = new HashMap<>();
        data.put("appointmentFee", getPersistedAppointmentFee());
        data.put("address", "");
        data.put("clinicName", "NexusHealth");

        String query = "SELECT clinic_name, address, appointment_fee FROM clinic_settings WHERE id = 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                data.put("clinicName", rs.getString("clinic_name"));
                data.put("address", rs.getString("address"));
                double fee = rs.getDouble("appointment_fee");
                if (fee > 0) {
                    data.put("appointmentFee", fee);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error reading clinic settings: " + e.getMessage());
        }
        return data;
    }

    public Doctor getDoctorByUserId(int userId) {
        String query = "SELECT d.id, d.user_id, u.full_name, d.specialization, u.profile_picture " +
                "FROM doctors d JOIN users u ON d.user_id = u.id " +
                "WHERE d.user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Doctor(
                            rs.getInt("id"),
                            rs.getInt("user_id"),
                            rs.getString("full_name"),
                            rs.getString("specialization"),
                            rs.getString("profile_picture"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching doctor by user id: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public String getDoctorAvailabilityStatusByUserId(int userId) {
        String query = "SELECT d.availability_status FROM doctors d WHERE d.user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String status = rs.getString("availability_status");
                    if (status != null && !status.trim().isEmpty()) {
                        return status.trim().toLowerCase();
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching doctor availability status: " + e.getMessage());
        }
        return "available";
    }

    public boolean updateDoctorAvailabilityByUserId(int userId, boolean available) {
        String query = "UPDATE doctors SET availability_status = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, available ? "available" : "unavailable");
            pstmt.setInt(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating doctor availability status: " + e.getMessage());
            return false;
        }
    }

    public List<Map<String, Object>> getDoctorAppointments(int doctorId, String date) {
        List<Map<String, Object>> appointments = new ArrayList<>();
        String query = "SELECT a.id, a.appointment_time, a.status, u.full_name AS patient_name, p.patient_code " +
                "FROM appointments a " +
                "JOIN patients p ON a.patient_id = p.id " +
                "JOIN users u ON p.user_id = u.id " +
                "WHERE a.doctor_id = ? AND a.appointment_date = ? " +
                "ORDER BY a.appointment_time";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, doctorId);
            pstmt.setString(2, date);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> appt = new HashMap<>();
                    appt.put("id", rs.getInt("id"));
                    appt.put("time", rs.getString("appointment_time"));
                    appt.put("status", rs.getString("status"));
                    appt.put("patientName", rs.getString("patient_name"));
                    appt.put("patientCode", rs.getString("patient_code"));
                    appointments.add(appt);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching doctor appointments: " + e.getMessage());
            e.printStackTrace();
        }
        return appointments;
    }

    /**
     * Get all appointments for a given date (for receptionist view)
     */
    public List<Map<String, Object>> getAppointmentsByDate(String date) {
        List<Map<String, Object>> appointments = new ArrayList<>();
        String query = "SELECT a.id, a.appointment_time, a.status, a.doctor_id, u.full_name AS patient_name, " +
                "p.patient_code, d.full_name AS doctor_name, doc.specialization " +
                "FROM appointments a " +
                "JOIN patients p ON a.patient_id = p.id " +
                "JOIN users u ON p.user_id = u.id " +
                "JOIN doctors doc ON a.doctor_id = doc.id " +
                "JOIN users d ON doc.user_id = d.id " +
                "WHERE a.appointment_date = ? " +
                "ORDER BY a.appointment_time";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, date);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> appt = new HashMap<>();
                    appt.put("id", rs.getInt("id"));
                    appt.put("doctorId", rs.getInt("doctor_id"));
                    appt.put("time", rs.getString("appointment_time"));
                    appt.put("status", rs.getString("status"));
                    appt.put("patientName", rs.getString("patient_name"));
                    appt.put("patientCode", rs.getString("patient_code"));
                    appt.put("doctorName", rs.getString("doctor_name"));
                    appt.put("specialization", rs.getString("specialization"));
                    appointments.add(appt);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching appointments by date: " + e.getMessage());
            e.printStackTrace();
        }
        return appointments;
    }

    /**
     * Reschedule an appointment to a new date and time
     */
    public boolean rescheduleAppointment(int appointmentId, String newDate, String newTime) {
        System.out.println("\n📅 APPOINTMENT SERVICE: Rescheduling appointment ID: " + appointmentId);

        // First, get the doctor ID from the appointment
        String getAppointmentQuery = "SELECT doctor_id FROM appointments WHERE id = ?";
        int doctorId = -1;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement getPstmt = conn.prepareStatement(getAppointmentQuery)) {
            getPstmt.setInt(1, appointmentId);

            try (ResultSet rs = getPstmt.executeQuery()) {
                if (rs.next()) {
                    doctorId = rs.getInt("doctor_id");
                }
            }

            if (doctorId == -1) {
                System.out.println("❌ Appointment not found");
                return false;
            }

            // Check if new time slot is available
            if (!isTimeSlotAvailable(doctorId, newDate, newTime)) {
                System.out.println("❌ Time slot is not available");
                return false;
            }

            // Update the appointment
            String updateQuery = "UPDATE appointments SET appointment_date = ?, appointment_time = ?, status = 'confirmed' WHERE id = ?";
            try (PreparedStatement updatePstmt = conn.prepareStatement(updateQuery)) {
                updatePstmt.setString(1, newDate);
                updatePstmt.setString(2, newTime);
                updatePstmt.setInt(3, appointmentId);

                int affectedRows = updatePstmt.executeUpdate();
                if (affectedRows > 0) {
                    System.out.println("✅ Appointment rescheduled to " + newDate + " at " + newTime);
                    return true;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error rescheduling appointment: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Cancel an appointment
     */
    public boolean cancelAppointment(int appointmentId) {
        System.out.println("\n❌ APPOINTMENT SERVICE: Cancelling appointment ID: " + appointmentId);

        String query = "UPDATE appointments SET status = 'cancelled' WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, appointmentId);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("✅ Appointment cancelled successfully");
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Error cancelling appointment: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Check in a patient for their appointment
     */
    public boolean checkInPatient(int appointmentId) {
        System.out.println("\n✅ APPOINTMENT SERVICE: Checking in patient for appointment ID: " + appointmentId);

        String query = "UPDATE appointments SET status = 'confirmed' WHERE id = ? AND status IN ('scheduled', 'pending')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, appointmentId);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("✅ Patient checked in successfully");
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Error checking in patient: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    // ===================== VALIDATION METHODS =====================

    /**
     * Validate appointment exists and return its current status
     */
    public String getAppointmentStatus(int appointmentId) {
        String query = "SELECT status FROM appointments WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, appointmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("status");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching appointment status: " + e.getMessage());
        }
        return null;
    }

    /**
     * Check if appointment can be rescheduled (not completed or cancelled)
     */
    public boolean canReschedule(int appointmentId) {
        String status = getAppointmentStatus(appointmentId);
        if (status == null) {
            return false;
        }
        return !status.equals("completed") && !status.equals("cancelled");
    }

    /**
     * Check if appointment can be cancelled (not already completed or cancelled)
     */
    public boolean canCancel(int appointmentId) {
        String status = getAppointmentStatus(appointmentId);
        if (status == null) {
            return false;
        }
        return !status.equals("completed") && !status.equals("cancelled");
    }

    /**
     * Check if doctor exists and is active
     */
    public boolean isDoctorValid(int doctorId) {
        String query = "SELECT COUNT(*) FROM doctors d JOIN users u ON d.user_id = u.id WHERE d.id = ? AND u.status = 'active'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, doctorId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error validating doctor: " + e.getMessage());
        }
        return false;
    }

    /**
     * Check if patient exists and is active
     */
    public boolean isPatientValid(int patientId) {
        String query = "SELECT COUNT(*) FROM patients p JOIN users u ON p.user_id = u.id WHERE p.id = ? AND u.status = 'active'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, patientId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error validating patient: " + e.getMessage());
        }
        return false;
    }

    /**
     * Get appointment details by ID
     */
    public Map<String, Object> getAppointmentById(int appointmentId) {
        String query = "SELECT a.id, a.patient_id, a.doctor_id, a.appointment_date, a.appointment_time, " +
                "a.status, a.notes, u.full_name AS patient_name, p.patient_code, " +
                "d.full_name AS doctor_name, doc.specialization " +
                "FROM appointments a " +
                "JOIN patients p ON a.patient_id = p.id " +
                "JOIN users u ON p.user_id = u.id " +
                "JOIN doctors doc ON a.doctor_id = doc.id " +
                "JOIN users d ON doc.user_id = d.id " +
                "WHERE a.id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, appointmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> appt = new HashMap<>();
                    appt.put("id", rs.getInt("id"));
                    appt.put("patientId", rs.getInt("patient_id"));
                    appt.put("doctorId", rs.getInt("doctor_id"));
                    appt.put("appointmentDate", rs.getString("appointment_date"));
                    appt.put("appointmentTime", rs.getString("appointment_time"));
                    appt.put("status", rs.getString("status"));
                    appt.put("notes", rs.getString("notes"));
                    appt.put("patientName", rs.getString("patient_name"));
                    appt.put("patientCode", rs.getString("patient_code"));
                    appt.put("doctorName", rs.getString("doctor_name"));
                    appt.put("specialization", rs.getString("specialization"));
                    return appt;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching appointment details: " + e.getMessage());
        }
        return null;
    }

    public List<Map<String, Object>> getAppointmentsForAdmin(Integer doctorId) {
        List<Map<String, Object>> appointments = new ArrayList<>();
        String query = "SELECT a.id, a.doctor_id, a.appointment_date, a.appointment_time, a.status, a.notes, " +
                "pu.full_name AS patient_name, p.patient_code, du.full_name AS doctor_name, doc.specialization " +
                "FROM appointments a " +
                "JOIN patients p ON a.patient_id = p.id " +
                "JOIN users pu ON p.user_id = pu.id " +
                "JOIN doctors doc ON a.doctor_id = doc.id " +
                "JOIN users du ON doc.user_id = du.id " +
                "WHERE (? IS NULL OR a.doctor_id = ?) " +
                "ORDER BY a.appointment_date DESC, a.appointment_time DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            if (doctorId == null) {
                pstmt.setNull(1, java.sql.Types.INTEGER);
                pstmt.setNull(2, java.sql.Types.INTEGER);
            } else {
                pstmt.setInt(1, doctorId);
                pstmt.setInt(2, doctorId);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> appt = new HashMap<>();
                    appt.put("id", rs.getInt("id"));
                    appt.put("doctorId", rs.getInt("doctor_id"));
                    appt.put("appointmentDate", rs.getString("appointment_date"));
                    appt.put("appointmentTime", normalizeTimeValue(rs.getString("appointment_time")));
                    appt.put("status", rs.getString("status"));
                    appt.put("notes", rs.getString("notes"));
                    appt.put("patientName", rs.getString("patient_name"));
                    appt.put("patientCode", rs.getString("patient_code"));
                    appt.put("doctorName", rs.getString("doctor_name"));
                    appt.put("specialization", rs.getString("specialization"));
                    appointments.add(appt);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching admin appointments: " + e.getMessage());
            e.printStackTrace();
        }

        return appointments;
    }

    public List<Map<String, Object>> getDoctorDirectoryForAdmin() {
        List<Map<String, Object>> doctors = new ArrayList<>();
        String query = "SELECT d.id, u.full_name, d.specialization " +
                "FROM doctors d JOIN users u ON d.user_id = u.id " +
                "WHERE u.role = 'doctor' AND u.status = 'active' " +
                "ORDER BY u.full_name ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("fullName", rs.getString("full_name"));
                row.put("specialization", rs.getString("specialization"));
                doctors.add(row);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching doctor directory for admin: " + e.getMessage());
            e.printStackTrace();
        }

        return doctors;
    }

    /**
     * Create an invoice for an appointment after payment
     */
    public boolean createAppointmentInvoice(int appointmentId, int patientId, int doctorId, String patientName) {
        System.out.println("\n💳 APPOINTMENT SERVICE: Creating invoice for appointment ID: " + appointmentId);

        // Generate unique invoice number
        String invoiceNumber = "INV-APT-" + System.currentTimeMillis();

        String invoiceInsertSql = "INSERT INTO invoices (invoice_number, patient_id, doctor_id, patient_name, consultation_type, " +
                "consultation_amount, subtotal, total_amount, status, payment_method, paid_at) " +
                "VALUES (?, ?, ?, ?, 'Appointment', ?, ?, ?, 'paid', 'card', CURRENT_TIMESTAMP)";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // Always read the latest persisted fee from DB so new invoices reflect receptionist updates.
            double effectiveFee = appointmentFee;
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM clinic_settings WHERE id = 1")) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        stmt.executeUpdate("INSERT INTO clinic_settings (id, appointment_fee) VALUES (1, 500.0)");
                    }
                }
                stmt.executeUpdate("UPDATE clinic_settings SET appointment_fee = 500.0 WHERE id = 1 AND (appointment_fee IS NULL OR appointment_fee <= 0)");
            } catch (Exception ignored) {
                // Best-effort safety net; DatabaseInitializer/ClinicSettingsService also ensure defaults.
            }

            try (PreparedStatement pstmt = conn.prepareStatement("SELECT appointment_fee FROM clinic_settings WHERE id = 1")) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        double persistedFee = rs.getDouble(1);
                        if (persistedFee > 0) {
                            effectiveFee = persistedFee;
                            // Keep runtime cache in sync for other call sites.
                            AppointmentService.setAppointmentFee(persistedFee);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️  Could not load appointment fee from DB, using cached fee: " + e.getMessage());
            }

            int invoiceId;
            try (PreparedStatement pstmt = conn.prepareStatement(invoiceInsertSql)) {
                pstmt.setString(1, invoiceNumber);
                pstmt.setInt(2, patientId);
                pstmt.setInt(3, doctorId);
                pstmt.setString(4, patientName);
                pstmt.setDouble(5, effectiveFee);  // consultation_amount
                pstmt.setDouble(6, effectiveFee);  // subtotal
                pstmt.setDouble(7, effectiveFee);  // total_amount

                int affectedRows = pstmt.executeUpdate();
                if (affectedRows <= 0) {
                    conn.rollback();
                    return false;
                }

                // SQLite JDBC driver may not support getGeneratedKeys(); use last_insert_rowid() instead.
                String idQuery = "SELECT last_insert_rowid() as id";
                try (PreparedStatement idPstmt = conn.prepareStatement(idQuery);
                     ResultSet rs = idPstmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }
                    invoiceId = rs.getInt("id");
                }
            }

            String specialization = null;
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT specialization FROM doctors WHERE id = ?")) {
                pstmt.setInt(1, doctorId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        specialization = rs.getString("specialization");
                    }
                }
            }

            String department = (specialization == null || specialization.trim().isEmpty()) ? "Appointments" : specialization;
            String transactionCode = "TXN-" + invoiceNumber;

            String txnInsertSql = "INSERT INTO transactions (invoice_id, transaction_code, type, department, amount, status, transacted_at) " +
                    "VALUES (?, ?, 'Appointment', ?, ?, 'settled', CURRENT_TIMESTAMP)";
            try (PreparedStatement pstmt = conn.prepareStatement(txnInsertSql)) {
                pstmt.setInt(1, invoiceId);
                pstmt.setString(2, transactionCode);
                pstmt.setString(3, department);
                pstmt.setDouble(4, effectiveFee);
                pstmt.executeUpdate();
            }

            conn.commit();
            System.out.println("✅ Invoice created successfully with number: " + invoiceNumber);
            System.out.println("✅ Transaction created successfully: " + transactionCode);
            return true;
        } catch (SQLException e) {
            System.err.println("Error creating appointment invoice: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("Error rolling back appointment invoice transaction: " + rollbackEx.getMessage());
                }
            }
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    System.err.println("Error closing connection: " + closeEx.getMessage());
                }
            }
        }
        return false;
    }

    private String normalizeTimeValue(String value) {
        if (value == null) {
            return null;
        }
        String raw = value.trim();
        if (raw.isEmpty()) {
            return null;
        }

        DateTimeFormatter out = DateTimeFormatter.ofPattern("HH:mm");
        DateTimeFormatter[] formats = new DateTimeFormatter[] {
                DateTimeFormatter.ofPattern("H:mm"),
                DateTimeFormatter.ofPattern("HH:mm"),
                DateTimeFormatter.ofPattern("h:mm a"),
                DateTimeFormatter.ofPattern("hh:mm a")
        };

        for (DateTimeFormatter fmt : formats) {
            try {
                return LocalTime.parse(raw.toUpperCase(), fmt).format(out);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private String computeAvailabilityStatus(String start, String end, int duration) {
        List<String> slots = generateSlots(start, end, duration > 0 ? duration : 30);
        return slots.isEmpty() ? "Unavailable" : "Available";
    }

    private List<String> generateSlots(String start, String end, int durationMinutes) {
        String normalizedStart = normalizeTimeValue(start);
        String normalizedEnd = normalizeTimeValue(end);
        if (normalizedStart == null) {
            normalizedStart = "09:00";
        }
        if (normalizedEnd == null) {
            normalizedEnd = "17:00";
        }

        LocalTime slotStart;
        LocalTime slotEnd;
        try {
            slotStart = LocalTime.parse(normalizedStart);
            slotEnd = LocalTime.parse(normalizedEnd);
        } catch (Exception e) {
            return Collections.emptyList();
        }

        if (!slotEnd.isAfter(slotStart) || durationMinutes <= 0) {
            return Collections.emptyList();
        }

        List<String> slots = new ArrayList<>();
        LocalTime cursor = slotStart;
        while (!cursor.plusMinutes(durationMinutes).isAfter(slotEnd)) {
            slots.add(cursor.format(DateTimeFormatter.ofPattern("HH:mm")));
            cursor = cursor.plusMinutes(durationMinutes);
        }
        return slots;
    }

    private boolean isTimeInsideDoctorSchedule(int doctorId, String normalizedTime) {
        Doctor doctor = getDoctorById(doctorId);
        if (doctor == null) {
            return false;
        }
        List<String> slots = generateSlots(
                doctor.getWorkingHoursStart(),
                doctor.getWorkingHoursEnd(),
                doctor.getConsultationDurationMin() > 0 ? doctor.getConsultationDurationMin() : 30
        );
        return slots.contains(normalizedTime);
    }

    private boolean isDoctorCurrentlyAvailable(int doctorId) {
        String query = "SELECT 1 FROM doctors d JOIN users u ON d.user_id = u.id " +
                "WHERE d.id = ? AND u.status = 'active' AND COALESCE(d.availability_status, 'available') = 'available'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, doctorId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Error checking doctor availability state: " + e.getMessage());
            return false;
        }
    }

    private double getPersistedAppointmentFee() {
        String query = "SELECT appointment_fee FROM clinic_settings WHERE id = 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                double fee = rs.getDouble(1);
                if (fee > 0) {
                    return fee;
                }
            }
        } catch (SQLException ignored) {
        }
        return getAppointmentFee();
    }
}
