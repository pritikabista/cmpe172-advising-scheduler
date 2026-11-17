package edu.sjsu.scheduler.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Retrieval-augmented Q&A over the advising policy/FAQ file (knowledge/advising-policies.md).
 *
 *  1. Ingest: split the file into one Document per "##" section, embed them with Gemini,
 *     store them in the vector store. Done once, on first use (so startup needs no API call).
 *  2. Retrieve: embed the question and find the most similar sections.
 *  3. Generate: ask Gemini to answer ONLY from those sections.
 */
@Service
public class PolicyKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(PolicyKnowledgeService.class);

    static final int TOP_K = 3;
    static final double SIMILARITY_THRESHOLD = 0.3;

    private static final String GROUNDING_PROMPT = """
            You are the SJSU Campus Advising Center help desk.
            Answer the student's question using ONLY the policy sections in the CONTEXT.
            If the context does not contain the answer, say you don't know and suggest contacting
            the advising office. Never invent rules, times, or numbers. Keep the answer short
            and mention which section(s) you used.
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final Resource knowledgeFile;
    private volatile boolean loaded = false;

    public PolicyKnowledgeService(VectorStore vectorStore, ChatModel chatModel,
                                  @Value("classpath:knowledge/advising-policies.md") Resource knowledgeFile) {
        this.vectorStore = vectorStore;
        this.chatClient = ChatClient.builder(chatModel).build();
        this.knowledgeFile = knowledgeFile;
    }

    /** Embeds and stores the knowledge file the first time it is needed. */
    synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        List<Document> sections = splitIntoSections(readKnowledgeFile());
        vectorStore.add(sections);
        loaded = true;
        log.info("RAG: ingested {} policy sections into the vector store", sections.size());
    }

    /** Vector search: the most relevant policy sections for a question. */
    public List<Document> retrieve(String question) {
        ensureLoaded();
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build());
    }

    /** Full RAG: retrieve, then let Gemini answer only from what was retrieved. */
    public PolicyAnswer answer(String question) {
        List<Document> docs = retrieve(question);
        if (docs.isEmpty()) {
            log.info("RAG: no relevant policy section for question '{}'", question);
            return new PolicyAnswer("I couldn't find that in the advising policies. "
                    + "Please contact the advising office at advising@sjsu.edu.", List.of());
        }
        String reply = chatClient.prompt()
                .system(GROUNDING_PROMPT)
                .user("CONTEXT:\n" + formatContext(docs) + "\n\nQUESTION: " + question)
                .call()
                .content();
        return new PolicyAnswer(reply, titles(docs));
    }

    /** Context block: each retrieved section with its title. Also used by the chat agent's policy tool. */
    public static String formatContext(List<Document> docs) {
        return docs.stream()
                .<String>map(d -> "### " + d.getMetadata().get("title") + "\n" + d.getText())
                .collect(Collectors.joining("\n\n"));
    }

    static List<String> titles(List<Document> docs) {
        return docs.stream().<String>map(d -> String.valueOf(d.getMetadata().get("title"))).toList();
    }

    /** One Document per "## " section, with the heading as metadata "title". */
    static List<Document> splitIntoSections(String markdown) {
        List<Document> docs = new ArrayList<>();
        String[] parts = markdown.split("(?m)^## ");
        for (int i = 1; i < parts.length; i++) {          // parts[0] is the file header
            String part = parts[i].strip();
            int newline = part.indexOf('\n');
            if (newline < 0) {
                continue;
            }
            String title = part.substring(0, newline).strip();
            String body = part.substring(newline + 1).strip();
            docs.add(new Document(title + ": " + body,
                    Map.of("title", title, "source", "advising-policies.md")));
        }
        return docs;
    }

    private String readKnowledgeFile() {
        try {
            return knowledgeFile.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read knowledge file", e);
        }
    }
}
