package edu.sjsu.scheduler.ai.reminder;

import edu.sjsu.scheduler.ai.rag.PolicyKnowledgeService;
import edu.sjsu.scheduler.model.AppointmentRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Asks Gemini for a short personalized reminder. It is grounded with the "what to bring" policy
 * retrieved from the knowledge base (RAG), so the preparation tips match the real rules.
 */
@Component
public class GeminiReminderMessageGenerator implements ReminderMessageGenerator {

    private static final Logger log = LoggerFactory.getLogger(GeminiReminderMessageGenerator.class);

    private static final String SYSTEM_PROMPT = """
            You write appointment reminder messages for the SJSU Campus Advising Center.
            Write 2-3 friendly sentences in plain text (no markdown, no emojis), under 400 characters.
            Greet the student by first name, include the date, time, advisor and location,
            give ONE preparation tip based only on the POLICY text, and say they can cancel
            from My Appointments at least 24 hours ahead if they can't come.
            Use only the facts given. Do not invent anything.
            """;

    private final ChatClient chatClient;
    private final PolicyKnowledgeService knowledge;

    public GeminiReminderMessageGenerator(ChatModel chatModel, PolicyKnowledgeService knowledge) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.knowledge = knowledge;
    }

    @Override
    public String generate(AppointmentRow a) {
        String policy = "";
        try {
            policy = PolicyKnowledgeService.formatContext(
                    knowledge.retrieve("What should I bring to a " + a.serviceName() + " appointment?"));
        } catch (RuntimeException e) {
            log.warn("Reminder: policy lookup failed, continuing without it ({})", e.toString());
        }
        String details = """
                Student: %s
                Service: %s
                Advisor: %s
                Date and time: %s
                Location: %s
                Student's note: %s
                POLICY:
                %s
                """.formatted(a.studentName(), a.serviceName(), a.advisorName(),
                a.startTime().format(DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a", Locale.US)),
                a.location(), a.notes() == null ? "(none)" : a.notes(), policy);

        String text = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(details)
                .call()
                .content();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Gemini returned an empty reminder");
        }
        return text.strip();
    }
}
