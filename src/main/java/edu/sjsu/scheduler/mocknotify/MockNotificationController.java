package edu.sjsu.scheduler.mocknotify;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Mock external notification service (a second controller inside the same Spring app).
 * The scheduler talks to it only over HTTP + JSON, exactly like it would talk to a real provider.
 *
 *   POST /mock-notify/api/notifications   receive one notification
 *   GET  /mock-notify/api/notifications   list what was received (for demos)
 *   GET  /mock-notify/api/health          is the service up?
 *   POST /mock-notify/admin/mode?value=DOWN|SLOW|UP   simulate failures
 */
@RestController
@RequestMapping("/mock-notify")
public class MockNotificationController {

    private final MockNotificationService service;
    private final long slowDelayMs;

    public MockNotificationController(MockNotificationService service,
                                      @Value("${mock-notify.slow-delay-ms:3000}") long slowDelayMs) {
        this.service = service;
        this.slowDelayMs = slowDelayMs;
    }

    @PostMapping("/api/notifications")
    public ResponseEntity<Map<String, Object>> receive(@Valid @RequestBody IncomingNotification request)
            throws InterruptedException {
        if (service.getMode() == MockMode.DOWN) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "UNAVAILABLE"));
        }
        if (service.getMode() == MockMode.SLOW) {
            Thread.sleep(slowDelayMs);   // longer than the client's read timeout
        }
        MockNotificationService.Result result = service.receive(request);
        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(Map.of(
                "notificationId", result.notification().notificationId(),
                "status", "ACCEPTED",
                "duplicate", result.duplicate()));
    }

    @GetMapping("/api/notifications")
    public List<StoredNotification> list() {
        return service.all();
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        if (service.getMode() == MockMode.DOWN) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
        }
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @PostMapping("/admin/mode")
    public Map<String, String> setMode(@RequestParam MockMode value) {
        service.setMode(value);
        return Map.of("mode", value.name());
    }

    /** Bad JSON body -> JSON 400 (this is an API, so no HTML error page). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> badRequest(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("status", "REJECTED", "error", "Invalid notification"));
    }
}
