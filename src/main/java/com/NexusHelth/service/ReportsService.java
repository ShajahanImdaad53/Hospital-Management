package com.NexusHelth.service;

import com.NexusHelth.dto.ReportsAnalyticsDTO;
import com.NexusHelth.dto.TransactionDTO;
import com.NexusHelth.util.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for handling Reports & Analytics data retrieval
 */
public class ReportsService {

    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Get complete reports and analytics data for a specific date
     */
    public ReportsAnalyticsDTO getReportsAnalyticsForDate(LocalDate date) {
        System.out.println("\n📊 REPORTS SERVICE: Fetching reports for date: " + date);
        
        // Debug: Check table counts
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Check transactions table
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) as cnt FROM transactions")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("  📊 Total transactions in DB: " + rs.getInt("cnt"));
                    }
                }
            }
            
            // Check invoices table
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) as cnt FROM invoices")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("  📊 Total invoices in DB: " + rs.getInt("cnt"));
                    }
                }
            }
            
            // Check appointments table
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) as cnt FROM appointments")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("  📊 Total appointments in DB: " + rs.getInt("cnt"));
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("  ⚠️ Debug check failed: " + e.getMessage());
        }

        Double dailyRevenue = getDailyRevenue(date);
        Double pharmacySales = getPharmacySales(date);
        Integer patientsVisited = getTotalPatientsVisited(date);
        List<TransactionDTO> recentTransactions = getTransactionsForDate(date);

        ReportsAnalyticsDTO reports = new ReportsAnalyticsDTO(
            dailyRevenue,
            pharmacySales,
            patientsVisited,
            recentTransactions
        );

        System.out.println("✅ Reports data retrieved successfully");
        System.out.println("   Daily Revenue: Rs." + reports.getDailyRevenue());
        System.out.println("   Pharmacy Sales: Rs." + reports.getPharmacySales());
        System.out.println("   Patients Visited: " + reports.getTotalPatientsVisited());
        System.out.println("   Recent Transactions: " + recentTransactions.size());

        return reports;
    }

    private String getDateCondition(String column) {
        if (DatabaseConnection.isPostgres()) {
            return "to_char(" + column + ", 'YYYY-MM-DD') = ?";
        }
        return "strftime('%Y-%m-%d', " + column + ") = ?";
    }

    /**
     * Calculate daily revenue from transactions
     */
    private Double getDailyRevenue(LocalDate date) {
        String dateStr = date.toString(); // yyyy-MM-dd
        String query = "SELECT SUM(amount) as total_revenue FROM transactions " +
                      "WHERE " + getDateCondition("transacted_at") + " AND status = 'settled'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, dateStr);
            System.out.println("  📊 Daily Revenue Query Date: " + dateStr);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Double revenue = rs.getDouble("total_revenue");
                    System.out.println("  📊 Daily Revenue Result (wasNull=" + rs.wasNull() + "): " + revenue);
                    if (rs.wasNull()) {
                        return 0.0;
                    }
                    return revenue > 0 ? revenue : 0.0;
                }
            }
        } catch (SQLException e) {
            System.out.println("❌ Error getting daily revenue: " + e.getMessage());
            e.printStackTrace();
        }

        return 0.0;
    }

    /**
     * Calculate pharmacy sales from invoices (pharmacy addons)
     */
    private Double getPharmacySales(LocalDate date) {
        String dateStr = date.toString(); // yyyy-MM-dd
        String query = "SELECT SUM(pharmacy_addons) as total_pharmacy FROM invoices " +
                      "WHERE " + getDateCondition("created_at") + " AND status = 'paid'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, dateStr);
            System.out.println("  💊 Pharmacy Sales Query Date: " + dateStr);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Double pharmacy = rs.getDouble("total_pharmacy");
                    System.out.println("  💊 Pharmacy Sales Result (wasNull=" + rs.wasNull() + "): " + pharmacy);
                    if (rs.wasNull()) {
                        return 0.0;
                    }
                    return pharmacy > 0 ? pharmacy : 0.0;
                }
            }
        } catch (SQLException e) {
            System.out.println("❌ Error getting pharmacy sales: " + e.getMessage());
            e.printStackTrace();
        }

        return 0.0;
    }

    /**
     * Get total patients visited for a specific date (completed appointments)
     */
    private Integer getTotalPatientsVisited(LocalDate date) {
        String dateStr = date.toString(); // yyyy-MM-dd
        String query = "SELECT COUNT(DISTINCT patient_id) as total_patients FROM appointments " +
                      "WHERE " + getDateCondition("appointment_date") + " AND status IN ('completed', 'confirmed')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, dateStr);
            System.out.println("  👥 Patients Visited Query Date: " + dateStr);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int patients = rs.getInt("total_patients");
                    System.out.println("  👥 Patients Visited Result: " + patients);
                    return patients;
                }
            }
        } catch (SQLException e) {
            System.out.println("❌ Error getting total patients visited: " + e.getMessage());
            e.printStackTrace();
        }

        return 0;
    }

    /**
     * Get all transactions for a specific date.
     */
    private List<TransactionDTO> getTransactionsForDate(LocalDate date) {
        List<TransactionDTO> transactions = new ArrayList<>();
        String query = "SELECT id, transaction_code, type, department, amount, status, transacted_at " +
                      "FROM transactions " +
                      "WHERE " + getDateCondition("transacted_at") + " " +
                      "ORDER BY transacted_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, date.toString());
            System.out.println("  💰 Getting transactions for date: " + date);

            try (ResultSet rs = pstmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    String transactionCode = rs.getString("transaction_code");

                    if (transactionCode == null || transactionCode.trim().isEmpty()) {
                        int txnId = rs.getInt("id");
                        transactionCode = "TXN-" + String.format("%05d", txnId);
                    }

                    String type = rs.getString("type");
                    if (type == null) type = "Unknown";

                    String department = rs.getString("department");
                    if (department == null) department = "N/A";

                    Double amount = rs.getDouble("amount");

                    String status = rs.getString("status");
                    if (status == null) status = "pending";

                    TransactionDTO transaction = new TransactionDTO(
                        transactionCode,
                        type,
                        department,
                        amount,
                        status,
                        parseDateTime(rs.getString("transacted_at"))
                    );

                    transactions.add(transaction);
                    System.out.println("    → Transaction: " + transactionCode + " | " + type + " | Rs." + String.format("%.2f", amount));
                }
                System.out.println("  💰 Total date-matched transactions found: " + count);
            }
        } catch (SQLException e) {
            System.out.println("❌ Error getting transactions for date: " + e.getMessage());
            e.printStackTrace();
        }

        return transactions;
    }

    /**
     * Get transactions within a date range
     */
    public List<TransactionDTO> getTransactionsForDateRange(LocalDate startDate, LocalDate endDate) {
        List<TransactionDTO> transactions = new ArrayList<>();
        String query = "SELECT transaction_code, type, department, amount, status, transacted_at " +
                      "FROM transactions " +
                      "WHERE DATE(transacted_at) BETWEEN ? AND ? " +
                      "ORDER BY transacted_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, startDate.toString());
            pstmt.setString(2, endDate.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    TransactionDTO transaction = new TransactionDTO(
                        rs.getString("transaction_code"),
                        rs.getString("type"),
                        rs.getString("department"),
                        rs.getDouble("amount"),
                        rs.getString("status"),
                        parseDateTime(rs.getString("transacted_at"))
                    );
                    transactions.add(transaction);
                }
            }
        } catch (SQLException e) {
            System.out.println("❌ Error getting transactions for date range: " + e.getMessage());
            e.printStackTrace();
        }

        return transactions;
    }

    /**
     * Parse datetime string to LocalDateTime
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        try {
            return LocalDateTime.parse(dateTimeStr, dateFormatter);
        } catch (Exception e) {
            System.out.println("⚠️ Warning: Could not parse datetime: " + dateTimeStr);
            return null;
        }
    }

    /**
     * Get all-time revenue (fallback when no data for specific date)
     */
    private Double getAllTimeRevenue() {
        String query = "SELECT SUM(amount) as total_revenue FROM transactions WHERE status = 'settled'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Double revenue = rs.getDouble("total_revenue");
                    System.out.println("  📊 All-Time Revenue Result: " + revenue);
                    if (rs.wasNull()) {
                        return 0.0;
                    }
                    return revenue > 0 ? revenue : 0.0;
                }
            }
        } catch (SQLException e) {
            System.out.println("❌ Error getting all-time revenue: " + e.getMessage());
        }

        return 0.0;
    }

    /**
     * Get all-time pharmacy sales (fallback when no data for specific date)
     */
    private Double getAllTimePharmacySales() {
        String query = "SELECT SUM(pharmacy_addons) as total_pharmacy FROM invoices WHERE status = 'paid'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Double pharmacy = rs.getDouble("total_pharmacy");
                    System.out.println("  💊 All-Time Pharmacy Sales Result: " + pharmacy);
                    if (rs.wasNull()) {
                        return 0.0;
                    }
                    return pharmacy > 0 ? pharmacy : 0.0;
                }
            }
        } catch (SQLException e) {
            System.out.println("❌ Error getting all-time pharmacy sales: " + e.getMessage());
        }

        return 0.0;
    }

    /**
     * Get all-time patients visited (fallback when no data for specific date)
     */
    private Integer getAllTimePatientsVisited() {
        String query = "SELECT COUNT(DISTINCT patient_id) as total_patients FROM appointments " +
                      "WHERE status IN ('completed', 'confirmed')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int patients = rs.getInt("total_patients");
                    System.out.println("  👥 All-Time Patients Visited Result: " + patients);
                    return patients;
                }
            }
        } catch (SQLException e) {
            System.out.println("❌ Error getting all-time patients visited: " + e.getMessage());
        }

        return 0;
    }
}
