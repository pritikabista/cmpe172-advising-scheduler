package edu.sjsu.scheduler.ai.agent;

import edu.sjsu.scheduler.exception.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 1 – conversational (reactive) agent.
 * Gemini decides which tools to call (possibly several in a row, e.g. search -> propose);
 * Spring AI runs the tool-calling loop. Chat memory gives multi-turn conversations.
 */
@Service
public class AdvisingAssistant {

    private static final Logger log = LoggerFactory.getLogger(AdvisingAssistant.class);
    static final int MAX_MESSAGE_LENGTH = 1000;

    private static final String SYSTEM_PROMPT = """
            You are the SJSU Campus Advising assistant. You help ONE logged-in student
            find, book, view, and cancel advising appointments. Today is %s (time zone %s).

            Rules:
            - Always use the tools for real data. Never invent slots, ids, times, or policies.
            - For "earliest", "this week", "tomorrow" etc., work out the dates from today and call
              findAvailableSlots with fromDate/toDate. You may call several tools in a row.
            - BOOKING/CANCELLING IS TWO STEPS. First call proposeBooking or proposeCancellation and show
              the user the summary, then ask "Shall I book it?". Call confirmPendingAction only when
              the user's NEW message clearly says yes. Never book or cancel on the first message.
            - If a slot is taken or a booking fails, explain it and offer the alternatives the tool returned.
            - If the request is unclear (no service, several matching slots), ask a short clarifying question.
            - For rules and policies (hours, cancellation, no-shows, what to bring) use searchAdvisingPolicies.
            - You can only act for the logged-in student. Keep answers short and friendly.
            """;

    private final ChatClient chatClient;
    private final AppointmentTools appointmentTools;
    private final PolicyTools policyTools;
    private final PendingActionStore pendingActions;
    private final Clock clock;

    public AdvisingAssistant(ChatModel chatModel, ChatMemory chatMemory, AppointmentTools appointmentTools,
                             PolicyTools policyTools, PendingActionStore pendingActions, Clock clock) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.appointmentTools = appointmentTools;
        this.policyTools = policyTools;
        this.pendingActions = pendingActions;
        this.clock = clock;
    }

    public ChatReply chat(String username, String clientConversationId, String message) {
        if (message == null || message.isBlank()) {
            throw new InvalidRequestException("Message can't be empty.");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new InvalidRequestException("Message must be " + MAX_MESSAGE_LENGTH + " characters or less.");
        }
        String clientId = (clientConversationId == null || clientConversationId.isBlank())
                ? UUID.randomUUID().toString() : clientConversationId;
        String conversationId = conversationKey(username, clientId);
        int turn = pendingActions.nextTurn(conversationId);

        try {
            String reply = chatClient.prompt()
                    .system(systemPrompt())
                    .user(message.strip())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .tools(appointmentTools, policyTools)
                    // who the agent acts as comes from the session, not from the model
                    .toolContext(Map.of(
                            ToolContextKeys.USERNAME, username,
                            ToolContextKeys.CONVERSATION_ID, conversationId,
                            ToolContextKeys.TURN, turn))
                    .call()
                    .content();
            log.info("Assistant answered {} (turn {})", username, turn);
            return new ChatReply(clientId, reply == null ? "" : reply);
        } catch (RuntimeException e) {
            // Gemini down, bad key, quota exceeded... the rest of the app keeps working.
            log.warn("Assistant call failed for {}: {}", username, e.toString());
            return new ChatReply(clientId, "Sorry, the AI assistant is unavailable right now. "
                    + "You can still book from the Available Slots page.");
        }
    }

    private String systemPrompt() {
        LocalDateTime now = LocalDateTime.now(clock);
        String today = now.format(DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd h:mm a", Locale.US));
        return SYSTEM_PROMPT.formatted(today, clock.getZone());
    }

    /**
     * Memory key = hash of (username + browser conversation id), as a UUID (36 chars, fits the
     * Spring AI memory table). One student can never load another student's conversation.
     */
    static String conversationKey(String username, String clientConversationId) {
        return UUID.nameUUIDFromBytes((username + ":" + clientConversationId)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }
}
