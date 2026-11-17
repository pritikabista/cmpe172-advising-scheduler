package edu.sjsu.scheduler.ai.agent;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Confirm-before-write guard, enforced in code (not just in the prompt).
 *
 * Every user message is a new "turn". A booking/cancellation proposed in turn N can only be
 * confirmed in turn N+1 or later, i.e. after the user has replied. So even if the model tried
 * to book on the first turn, the confirm tool would refuse.
 */
@Component
public class PendingActionStore {

    static final int EXPIRY_MINUTES = 15;

    private final Map<String, PendingAction> pending = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> turns = new ConcurrentHashMap<>();
    private final Clock clock;

    public PendingActionStore(Clock clock) {
        this.clock = clock;
    }

    /** Called once per user message. */
    public int nextTurn(String conversationId) {
        return turns.computeIfAbsent(conversationId, k -> new AtomicInteger()).incrementAndGet();
    }

    /** Replaces any earlier proposal in this conversation. */
    public void propose(String conversationId, PendingAction action) {
        pending.put(conversationId, action);
    }

    public Optional<PendingAction> peek(String conversationId) {
        PendingAction action = pending.get(conversationId);
        if (action != null && action.createdAt().plusMinutes(EXPIRY_MINUTES).isBefore(LocalDateTime.now(clock))) {
            pending.remove(conversationId);
            return Optional.empty();
        }
        return Optional.ofNullable(action);
    }

    /** True only if the proposal was made in an earlier turn (the user has answered since). */
    public static boolean userHasRepliedSince(PendingAction action, int currentTurn) {
        return currentTurn > action.createdTurn();
    }

    public void clear(String conversationId) {
        pending.remove(conversationId);
    }
}
