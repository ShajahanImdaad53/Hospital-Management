package com.NexusHelth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.NexusHelth.util.DatabaseConnection;

import java.sql.Connection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toString());
        response.put("engine", DatabaseConnection.isPostgres() ? "PostgreSQL" : "SQLite");

        boolean dbOk = false;
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                dbOk = true;
            }
        } catch (Exception e) {
            dbOk = false;
        }

        response.put("database", dbOk ? "CONNECTED" : "DISCONNECTED");

        if (dbOk) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(503).body(response);
        }
    }
}
