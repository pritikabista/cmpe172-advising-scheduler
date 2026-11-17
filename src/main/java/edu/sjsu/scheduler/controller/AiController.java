package edu.sjsu.scheduler.controller;

import edu.sjsu.scheduler.ai.agent.AdvisingAssistant;
import edu.sjsu.scheduler.ai.agent.ChatReply;
import edu.sjsu.scheduler.ai.rag.PolicyAnswer;
import edu.sjsu.scheduler.ai.rag.PolicyKnowledgeService;
import edu.sjsu.scheduler.ai.reminder.ReminderAgent;
import edu.sjsu.scheduler.ai.reminder.ReminderRunResult;
import edu.sjsu.scheduler.exception.AppException;
import edu.sjsu.scheduler.exception.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * JSON endpoints for Milestone 4.
 *   POST /api/chat                    Agent 1 (students only)        {conversationId?, message}
 *   POST /api/ask                     grounded policy Q&A (RAG)       {question}
 *   POST /api/agents/reminders/run    manual trigger for Agent 2 (advisors only)
 */
@RestController
@RequestMapping("/api")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final AdvisingAssistant assistant;
    private final PolicyKnowledgeService knowledge;
    private final ReminderAgent reminderAgent;

    public AiController(AdvisingAssistant assistant, PolicyKnowledgeService knowledge, ReminderAgent reminderAgent) {
        this.assistant = assistant;
        this.knowledge = knowledge;
        this.reminderAgent = reminderAgent;
    }

    public record ChatRequest(String conversationId, String message) {}

    public record AskRequest(String question) {}

    @PostMapping("/chat")
    public ChatReply chat(@RequestBody ChatRequest request, Principal principal) {
        // the agent acts as the logged-in user (from the session), never as someone named in the message
        return assistant.chat(principal.getName(), request.conversationId(), request.message());
    }

    @PostMapping("/ask")
    public ResponseEntity<PolicyAnswer> ask(@RequestBody AskRequest request) {
        String q = request.question();
        if (q == null || q.isBlank() || q.length() > 500) {
            throw new InvalidRequestException("Question must be 1-500 characters.");
        }
        try {
            return ResponseEntity.ok(knowledge.answer(q.strip()));
        } catch (RuntimeException e) {
            log.warn("Policy Q&A failed: {}", e.toString());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new PolicyAnswer(
                    "The policy assistant is unavailable right now. Please try again later.", List.of()));
        }
    }

    @PostMapping("/agents/reminders/run")
    public ReminderRunResult runReminders() {
        return reminderAgent.run("manual-api");
    }

    /** JSON errors for this API (the HTML error page is for the website). */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, Object>> handle(AppException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("status", ex.getStatus().value(), "error", ex.getMessage()));
    }
}
