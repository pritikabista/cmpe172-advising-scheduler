package edu.sjsu.scheduler.ai.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The knowledge file is split into one vector-store document per "##" section. */
class PolicyKnowledgeServiceTest {

    @Test
    void knowledgeFileIsSplitIntoTitledSections() throws Exception {
        String md = new ClassPathResource("knowledge/advising-policies.md").getContentAsString(StandardCharsets.UTF_8);

        List<Document> docs = PolicyKnowledgeService.splitIntoSections(md);

        assertThat(docs).hasSizeGreaterThanOrEqualTo(10);
        assertThat(PolicyKnowledgeService.titles(docs)).contains("Cancellation policy", "What to bring");
        Document cancel = docs.stream()
                .filter(d -> "Cancellation policy".equals(d.getMetadata().get("title"))).findFirst().orElseThrow();
        assertThat(cancel.getText()).contains("24 hours");
    }
}
