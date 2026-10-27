package edu.sjsu.scheduler.observability;

import edu.sjsu.scheduler.notification.NotificationGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /health
 *   database down              -> 503, status DOWN      (the app can't work)
 *   notification service down  -> 200, status DEGRADED  (bookings still work, emails are queued)
 *   everything up              -> 200, status UP
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbc;
    private final NotificationGateway notificationGateway;

    public HealthController(JdbcTemplate jdbc, NotificationGateway notificationGateway) {
        this.jdbc = jdbc;
        this.notificationGateway = notificationGateway;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> database = checkDatabase();
        boolean dbUp = "UP".equals(database.get("status"));
        boolean notificationsUp = notificationGateway.isAvailable();

        String overall = !dbUp ? "DOWN" : (notificationsUp ? "UP" : "DEGRADED");

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("database", database);
        components.put("notificationService", Map.of("status", notificationsUp ? "UP" : "DOWN"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", overall);
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("components", components);

        return ResponseEntity.status(dbUp ? 200 : 503).body(body);
    }

    /** Runs SELECT 1 and measures how long it took. */
    private Map<String, Object> checkDatabase() {
        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.nanoTime();
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            result.put("status", "UP");
            result.put("responseTimeMs", (System.nanoTime() - start) / 1_000_000);
        } catch (DataAccessException e) {
            log.error("Health check: database unreachable ({})", e.getClass().getSimpleName());
            result.put("status", "DOWN");
            result.put("error", "Database unreachable");
        }
        return result;
    }
}
