package io.epiphaneia.agent.internal.orchestration;

import io.epiphaneia.agent.api.model.*;
import io.epiphaneia.agent.api.repository.EvidenceRepository;
import io.epiphaneia.agent.api.repository.FixSuggestionRepository;
import io.epiphaneia.agent.api.repository.RootCauseHypothesisRepository;
import io.epiphaneia.agent.internal.llm.LlmClient;
import io.epiphaneia.agent.internal.llm.PromptTemplateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportSynthesizerImplTest {

    private LlmClient llmClient;
    private PromptTemplateManager promptManager;
    private EvidenceRepository evidenceRepo;
    private RootCauseHypothesisRepository hypothesisRepo;
    private FixSuggestionRepository suggestionRepo;
    private ReportSynthesizerImpl reportSynthesizer;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        promptManager = mock(PromptTemplateManager.class);
        evidenceRepo = mock(EvidenceRepository.class);
        hypothesisRepo = mock(RootCauseHypothesisRepository.class);
        suggestionRepo = mock(FixSuggestionRepository.class);

        reportSynthesizer = new ReportSynthesizerImpl(llmClient, promptManager,
                evidenceRepo, hypothesisRepo, suggestionRepo);

        when(promptManager.interpolate(eq("report"), anyMap())).thenReturn("report template");
    }

    @Test
    @DisplayName("LLM success: returns markdown report")
    void synthesizeLlmSuccess() throws Exception {
        String expected = "# Diagnostic Report\n\nTest content";
        when(llmClient.call(anyString())).thenReturn(expected);

        Conversation conv = createConversationWithMessages();
        Message diag = findAgentMessage(conv);

        when(evidenceRepo.findByMessageOrderByCollectedAtAsc(diag)).thenReturn(List.of());
        when(hypothesisRepo.findByMessageOrderByRankAsc(diag)).thenReturn(List.of());
        when(suggestionRepo.findByMessage(diag)).thenReturn(List.of());

        String result = reportSynthesizer.synthesize(conv);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("LLM failure: falls back to template report with all sections")
    void synthesizeLlmFailureFallsBackToTemplate() throws Exception {
        when(llmClient.call(anyString())).thenThrow(new RuntimeException("LLM error"));

        Conversation conv = createConversationWithMessages();
        Message diag = findAgentMessage(conv);

        when(evidenceRepo.findByMessageOrderByCollectedAtAsc(diag)).thenReturn(List.of());
        when(hypothesisRepo.findByMessageOrderByRankAsc(diag)).thenReturn(List.of());
        when(suggestionRepo.findByMessage(diag)).thenReturn(List.of());

        String result = reportSynthesizer.synthesize(conv);

        assertTrue(result.contains("# Diagnostic Report"));
        assertTrue(result.contains("## Summary"));
        assertTrue(result.contains("## Evidence Collected"));
        assertTrue(result.contains("## Root Cause Analysis"));
        assertTrue(result.contains("## Recommendations"));
        assertTrue(result.contains("## Risk Assessment"));
    }

    @Test
    @DisplayName("empty conversation messages: returns helpful message")
    void synthesizeEmptyConversation() {
        Conversation conv = new Conversation();
        conv.setTitle("test");
        // messages default to empty list

        String result = reportSynthesizer.synthesize(conv);
        assertTrue(result.contains("No diagnostic data available"));
    }

    @Test
    @DisplayName("no AGENT role message: returns not-ready message")
    void synthesizeNoAgentMessage() {
        Conversation conv = new Conversation();
        conv.setTitle("test");

        Message userMsg = new Message();
        userMsg.setRole("USER");
        userMsg.setContent("hello");

        try {
            var msgsField = Conversation.class.getDeclaredField("messages");
            msgsField.setAccessible(true);
            msgsField.set(conv, new ArrayList<>(List.of(userMsg)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String result = reportSynthesizer.synthesize(conv);
        assertTrue(result.contains("No diagnosis results available"));
    }

    @Test
    @DisplayName("non-Conversation input: returns error message")
    void synthesizeNonConversationObject() {
        String result = reportSynthesizer.synthesize("not a conversation");
        assertTrue(result.contains("Unable to synthesize report"));
    }

    @Test
    @DisplayName("template report handles null risk fields gracefully")
    void templateHandlesNullRiskFields() throws Exception {
        when(llmClient.call(anyString())).thenThrow(new RuntimeException("LLM error"));

        Conversation conv = createConversationWithMessages();
        Message diag = findAgentMessage(conv);
        diag.setRiskLevel(null);
        diag.setRiskImpact(null);
        diag.setRiskUrgency(null);

        when(evidenceRepo.findByMessageOrderByCollectedAtAsc(diag)).thenReturn(List.of());
        when(hypothesisRepo.findByMessageOrderByRankAsc(diag)).thenReturn(List.of());
        when(suggestionRepo.findByMessage(diag)).thenReturn(List.of());

        String result = reportSynthesizer.synthesize(conv);
        assertTrue(result.contains("N/A") || result.contains("Not assessed"),
                "Should handle null risk fields");
    }

    private Conversation createConversationWithMessages() throws Exception {
        Application app = new Application();
        app.setName("test-app");

        Conversation conv = new Conversation();
        conv.setApplication(app);
        conv.setTitle("Why is service slow?");

        Message userMsg = new Message();
        userMsg.setRole("USER");
        userMsg.setContent("Why is service slow?");

        Message agentMsg = new Message();
        agentMsg.setRole("AGENT");
        agentMsg.setContent("Diagnosis result");
        agentMsg.setDiagnosisState("COMPLETED");
        agentMsg.setRiskLevel("HIGH");
        agentMsg.setRiskImpact("Service outage");
        agentMsg.setRiskUrgency("Immediate");
        agentMsg.setCompletedAt(Instant.now());

        var createdAtField = Message.class.getDeclaredField("createdAt");
        createdAtField.setAccessible(true);
        createdAtField.set(userMsg, Instant.now().minusSeconds(60));
        createdAtField.set(agentMsg, Instant.now().minusSeconds(30));

        var msgsField = Conversation.class.getDeclaredField("messages");
        msgsField.setAccessible(true);
        msgsField.set(conv, new ArrayList<>(List.of(userMsg, agentMsg)));
        return conv;
    }

    private static Message findAgentMessage(Conversation conv) {
        return conv.getMessages().stream()
                .filter(m -> "AGENT".equals(m.getRole()))
                .findFirst().orElseThrow();
    }
}
