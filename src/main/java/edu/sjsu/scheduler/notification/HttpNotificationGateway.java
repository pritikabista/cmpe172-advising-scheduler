package edu.sjsu.scheduler.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * HTTP implementation of the gateway (the remote proxy for the notification service).
 *
 * Fault handling:
 *  - timeout: connect 500 ms, read 1000 ms, so a slow service can't hang a booking request
 *  - retry:   up to 3 attempts with a short backoff, only for timeouts / connection errors / 5xx
 *  - no retry for 4xx: the request itself is wrong, sending it again won't help
 * The caller (AppointmentNotifier) handles the fallback when this throws.
 */
@Component
public class HttpNotificationGateway implements NotificationGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpNotificationGateway.class);

    private final RestClient restClient;
    private final Environment env;
    private final int maxAttempts;
    private final long backoffMs;

    public HttpNotificationGateway(Environment env,
                                   @Value("${notification.connect-timeout-ms:500}") long connectTimeoutMs,
                                   @Value("${notification.read-timeout-ms:1000}") long readTimeoutMs,
                                   @Value("${notification.max-attempts:3}") int maxAttempts,
                                   @Value("${notification.backoff-ms:200}") long backoffMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.env = env;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    /** Read on every call so it also works when the port is only known after startup (tests). */
    private String baseUrl() {
        return env.getRequiredProperty("notification.base-url");
    }

    @Override
    public NotificationResponse send(NotificationRequest request) {
        RestClientException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                NotificationResponse response = restClient.post()
                        .uri(baseUrl() + "/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", request.eventId())
                        .body(request)
                        .retrieve()
                        .body(NotificationResponse.class);
                log.info("Notification {} delivered (attempt {}/{})", request.eventId(), attempt, maxAttempts);
                return response;
            } catch (HttpClientErrorException e) {
                // 4xx: our request is invalid -> retrying would give the same answer
                throw new NotificationDeliveryException(
                        "Notification rejected with " + e.getStatusCode(), e);
            } catch (RestClientException e) {
                // timeout, connection refused, or 5xx -> worth another try
                lastError = e;
                log.warn("Notification {} attempt {}/{} failed: {}",
                        request.eventId(), attempt, maxAttempts, e.getClass().getSimpleName());
                if (attempt < maxAttempts && !sleep(backoffMs * attempt)) {
                    break;
                }
            }
        }
        throw new NotificationDeliveryException(
                "Notification " + request.eventId() + " failed after " + maxAttempts + " attempts", lastError);
    }

    @Override
    public boolean isAvailable() {
        try {
            restClient.get().uri(baseUrl() + "/api/health").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    private static boolean sleep(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
