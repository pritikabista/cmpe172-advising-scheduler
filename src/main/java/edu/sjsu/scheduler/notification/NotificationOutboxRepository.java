package edu.sjsu.scheduler.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Fallback storage: notifications that could not be delivered are saved here
 * and re-sent later by NotificationRetryJob.
 */
@Repository
public class NotificationOutboxRepository {

    private final JdbcTemplate jdbc;

    public NotificationOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Idempotent: saving the same eventId twice keeps just one row. */
    public void savePending(NotificationRequest request, String error) {
        String sql = """
            INSERT INTO notification_outbox (event_id, recipient, type, message, status, attempts, last_error)
            VALUES (?, ?, ?, ?, 'PENDING', 1, ?)
            ON CONFLICT (event_id) DO NOTHING
            """;
        jdbc.update(sql, request.eventId(), request.recipient(), request.type(), request.message(), shorten(error));
    }

    public List<PendingNotification> findPending(int limit) {
        String sql = """
            SELECT event_id, recipient, type, message, attempts
            FROM notification_outbox
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT ?
            """;
        return jdbc.query(sql, (rs, rowNum) -> new PendingNotification(
                        new NotificationRequest(
                                rs.getString("event_id"),
                                rs.getString("recipient"),
                                rs.getString("type"),
                                rs.getString("message")),
                        rs.getInt("attempts")),
                limit);
    }

    public void markSent(String eventId) {
        jdbc.update("""
            UPDATE notification_outbox
            SET status = 'SENT', last_error = NULL, updated_at = LOCALTIMESTAMP
            WHERE event_id = ?
            """, eventId);
    }

    /** One more failed round. After maxAttempts rounds we give up and mark it FAILED. */
    public void recordFailure(String eventId, String error, int maxAttempts) {
        jdbc.update("""
            UPDATE notification_outbox
            SET attempts   = attempts + 1,
                last_error = ?,
                status     = CASE WHEN attempts + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END,
                updated_at = LOCALTIMESTAMP
            WHERE event_id = ?
            """, shorten(error), maxAttempts, eventId);
    }

    public int countByStatus(String status) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_outbox WHERE status = ?", Integer.class, status);
        return n == null ? 0 : n;
    }

    public String findStatus(String eventId) {
        return jdbc.queryForList("SELECT status FROM notification_outbox WHERE event_id = ?",
                String.class, eventId).stream().findFirst().orElse(null);
    }

    private static String shorten(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 255 ? s : s.substring(0, 255);
    }
}
