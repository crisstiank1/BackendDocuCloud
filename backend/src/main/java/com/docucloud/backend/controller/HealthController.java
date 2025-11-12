package com.docucloud.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador REST para verificar el estado de salud (Health Check) de la aplicación.
 * Permite verificar si el servicio está activo y si la conexión a la base de datos es exitosa.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    // Inyecta JdbcTemplate para interactuar con la base de datos y verificar la conexión
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Endpoint principal para el chequeo de salud.
     * @return ResponseEntity con el estado general de la aplicación y la base de datos.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "DocuCloud Backend");

        boolean dbIsUp = checkDatabaseConnection();

        Map<String, Object> dbStatus = new HashMap<>();
        dbStatus.put("status", dbIsUp ? "UP" : "DOWN");
        dbStatus.put("details", dbIsUp ? "Database connection successful" : "Database connection failed");
        response.put("database", dbStatus);

        HttpStatus httpStatus = dbIsUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;

        return new ResponseEntity<>(response, httpStatus);
    }

    /**
     * Intenta ejecutar una consulta simple para verificar la conectividad con la base de datos.
     * @return true si la conexión es exitosa, false en caso contrario.
     */
    private boolean checkDatabaseConnection() {
        try {
            // Consulta simple (ejecutar 'SELECT 1') para confirmar que la conexión JDBC funciona.
            // Si la consulta se ejecuta sin excepción, la conexión está activa.
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            // Si ocurre alguna excepción (por ejemplo, error de conexión, credenciales incorrectas),
            // la base de datos se considera no disponible.
            System.err.println("Database health check failed: " + e.getMessage());
            return false;
        }
    }
}
