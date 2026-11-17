package edu.sjsu.scheduler.ai.agent;

import java.time.LocalDateTime;

/**
 * A write the agent has proposed but the user has not confirmed yet.
 * createdTurn = the chat turn in which it was proposed.
 */
public record PendingAction(Type type, long targetId, String notes, String summary,
                            int createdTurn, LocalDateTime createdAt) {

    public enum Type { BOOK, CANCEL }
}
