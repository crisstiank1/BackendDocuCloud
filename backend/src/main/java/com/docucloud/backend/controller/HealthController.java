package com.docucloud.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Utilizamos los paquetes de JAKARTA, la nueva especificación para Java EE / Spring Boot 3+
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Controlador REST para verificar el estado de salud de la aplicación y la conexión con la base de datos.
 * Accesible en /api/health.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    // JdbcTemplate se usa para interactuar con la DB y es automáticamente configurado por Spring Boot
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        System.out.println("HealthController inicializado.");
    }

    /**
     * Endpoint que devuelve el estado general de la aplicación.
     * Incluye información de la conexión a la base de datos.
     */
    @GetMapping("/health")
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("app_status", "UP");
        status.put("service", "DocuCloud Backend");
        status.put("version", "0.0.1");

        // Verificación de la Base de Datos
        status.put("database", checkDatabaseConnection());

        return status;
    }

    /**
     * Intenta ejecutar una consulta simple para verificar la conectividad con PostgreSQL.
     */
    private Map<String, String> checkDatabaseConnection() {
        Map<String, String> dbStatus = new HashMap<>();
        try {
            // Ejecuta una consulta simple para probar la conexión
            // En PostgreSQL, SELECT 1 es una forma ligera de verificar la conexión.
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            if (result != null && result == 1) {
                dbStatus.put("status", "UP");
                dbStatus.put("message", "PostgreSQL connection successful and query executed.");
            } else {
                dbStatus.put("status", "DOWN");
                dbStatus.put("message", "PostgreSQL connection successful but query returned unexpected result.");
            }
        } catch (Exception e) {
            // Captura cualquier excepción de conexión (e.g., DB no lista o credenciales incorrectas)
            dbStatus.put("status", "DOWN");
            dbStatus.put("message", "Connection error: " + e.getMessage());
            System.err.println("Error al verificar la conexión de la DB: " + e.getMessage());
        }
        return dbStatus;
    }
}
