package edu.sjsu.scheduler.ai.agent;

import edu.sjsu.scheduler.ai.rag.PolicyKnowledgeService;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/** Lets the chat agent look up advising rules (RAG retrieval) instead of guessing them. */
@Component
public class PolicyTools {

    private final PolicyKnowledgeService knowledge;

    public PolicyTools(PolicyKnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    @Tool(description = """
            Search the advising center's official policies and FAQ (hours, cancellation and no-show
            rules, services, what to bring, contacts). Use it for any policy question.""")
    public String searchAdvisingPolicies(@ToolParam(description = "The student's question") String question) {
        List<Document> docs = knowledge.retrieve(question);
        return docs.isEmpty() ? "No matching policy found." : PolicyKnowledgeService.formatContext(docs);
    }
}
