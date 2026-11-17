package edu.sjsu.scheduler.ai.agent;

/**
 * Keys of the ToolContext map. The controller fills these from the logged-in session;
 * they are never sent to the model, so the model cannot change who it acts as.
 */
public final class ToolContextKeys {

    public static final String USERNAME = "username";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String TURN = "turn";

    private ToolContextKeys() {}
}
