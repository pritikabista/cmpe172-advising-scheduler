package edu.sjsu.scheduler.mocknotify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stand-in for an external messaging system (email/SMS provider).
 * It only logs and stores notifications in memory; nothing is really sent.
 */
@Service
public class MockNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MockNotificationService.class);

    /** eventId -> notification. Keyed by eventId so the same event is only stored once. */
    private final Map<String, StoredNotification> received = new ConcurrentHashMap<>();
    private final AtomicReference<MockMode> mode;

    public MockNotificationService(@Value("${mock-notify.mode:UP}") MockMode initialMode) {
        this.mode = new AtomicReference<>(initialMode);
    }

    public record Result(StoredNotification notification, boolean duplicate) {}

    /** Idempotent: if this eventId was already received, return the stored one and do nothing else. */
    public Result receive(IncomingNotification in) {
        StoredNotification fresh = new StoredNotification(UUID.randomUUID().toString(), in.eventId(),
                in.recipient(), in.type(), in.message(), LocalDateTime.now());
        StoredNotification existing = received.putIfAbsent(in.eventId(), fresh);
        if (existing != null) {
            log.info("[mock-notify] duplicate event {} ignored", in.eventId());
            return new Result(existing, true);
        }
        log.info("[mock-notify] {} to {}: {}", in.type(), in.recipient(), in.message());
        return new Result(fresh, false);
    }

    public List<StoredNotification> all() {
        List<StoredNotification> list = new ArrayList<>(received.values());
        list.sort(Comparator.comparing(StoredNotification::receivedAt).reversed());
        return list;
    }

    public MockMode getMode() {
        return mode.get();
    }

    public void setMode(MockMode newMode) {
        log.warn("[mock-notify] mode changed to {}", newMode);
        mode.set(newMode);
    }

    /** Used by tests to start from an empty inbox. */
    public void clear() {
        received.clear();
    }
}
