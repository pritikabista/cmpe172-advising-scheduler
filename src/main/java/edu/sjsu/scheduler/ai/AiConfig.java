package edu.sjsu.scheduler.ai;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.PostgresChatMemoryRepositoryDialect;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spring AI beans. The Gemini ChatModel and EmbeddingModel themselves are auto-configured
 * by the Google GenAI starters from application.properties.
 */
@Configuration
public class AiConfig {

    /**
     * Conversation memory for Agent 1, stored in PostgreSQL (table SPRING_AI_CHAT_MEMORY),
     * so a conversation survives an app restart (bonus feature). Keeps the last N messages.
     */
    @Bean
    public ChatMemory chatMemory(JdbcTemplate jdbcTemplate,
                                 @Value("${assistant.memory.max-messages:20}") int maxMessages) {
        JdbcChatMemoryRepository repository = JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                .dialect(new PostgresChatMemoryRepositoryDialect())
                .build();
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(maxMessages)
                .build();
    }

    /** In-memory vector store for the advising policy/FAQ documents (RAG). Embeddings come from Gemini. */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
