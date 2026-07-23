package io.epiphaneia.agent.internal.orchestration;

import io.epiphaneia.agent.api.DiagnosisContext;
import io.epiphaneia.llm.api.DiagnosisSseEventPublisher;
import io.epiphaneia.domain.internal.entity.*;
import io.epiphaneia.domain.internal.repository.*;
import io.epiphaneia.llm.internal.client.LlmClient;
import io.epiphaneia.llm.internal.routing.ModelRouter;
import io.epiphaneia.llm.internal.template.PromptTemplateManager;
import io.epiphaneia.infra.api.ConnectorRegistry;
import io.epiphaneia.infra.api.connector.Connector;
import io.epiphaneia.infra.api.connector.QueryResult;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DiagnosisOrchestratorImplTest {

    private LlmClient llmClient;
    private PromptTemplateManager promptManager;
    private ModelRouter modelRouter;
    private ConnectorRegistry connectorRegistry;
    private EntityManager em;
    private EvidenceRepository evidenceRepo;
    private RootCauseHypothesisRepository hypothesisRepo;
    private FixSuggestionRepository suggestionRepo;
    private DiagnosisOrchestratorImpl orchestrator;

    private Conversation conversation;
    private Message message;
    private Application application;
    private LlmProvider llmProvider;
    private DataSource prometheusDs;
    private List<String> sseEvents;
    private DiagnosisSseEventPublisher ssePublisher;

    @BeforeEach
    void setUp() throws Exception {
        llmClient = mock(LlmClient.class);
        promptManager = mock(PromptTemplateManager.class);
        modelRouter = mock(ModelRouter.class);
        connectorRegistry = mock(ConnectorRegistry.class);
        em = mock(EntityManager.class);
        evidenceRepo = mock(EvidenceRepository.class);
        hypothesisRepo = mock(RootCauseHypothesisRepository.class);
        suggestionRepo = mock(FixSuggestionRepository.class);

        orchestrator = new DiagnosisOrchestratorImpl(llmClient, promptManager,
                modelRouter, connectorRegistry, em, evidenceRepo, hypothesisRepo, suggestionRepo);

        application = new Application();
        application.setName("test-service");

        llmProvider = new LlmProvider();
        llmProvider.setProvider("OPENAI");
        llmProvider.setModelName("gpt-4o");

        prometheusDs = new DataSource();
        prometheusDs.setType("PROMETHEUS");
        prometheusDs.setName("metrics");
        prometheusDs.setUrl("http://prom:9090");

        conversation = new Conversation();
        conversation.setApplication(application);
        conversation.setTitle("Diagnosis test");

        message = new Message();
        message.setConversation(conversation);
        message.setRole("AGENT");
        message.setDiagnosisState("CREATED");
        message.setContent("Why is service slow?");

        setEntityId(conversation, UUID.randomUUID());
        setEntityId(message, UUID.randomUUID());

        sseEvents = new ArrayList<>();
        ssePublisher = new DiagnosisSseEventPublisher() {
            public void state(UUID cid, UUID mid, String s) { sseEvents.add("state:" + s); }
            public void step(UUID cid, UUID mid, String d) { sseEvents.add("step:" + d); }
            public void token(UUID cid, UUID mid, String t) {}
            public void done(UUID cid, UUID mid) { sseEvents.add("done"); }
            public void error(UUID cid, UUID mid, String r) { sseEvents.add("error:" + r); }
            public void close(UUID cid) { sseEvents.add("close"); }
        };

        Connector promConnector = mock(Connector.class);
        when(promConnector.query(any())).thenReturn(new QueryResult() {});
        when(connectorRegistry.getConnector("PROMETHEUS")).thenReturn(promConnector);

        when(llmClient.call(anyString(), anyString(), any(LlmProvider.class))).thenReturn("Query plan");
        when(llmClient.call(anyString(), any(LlmProvider.class)))
                .thenReturn("Hypothesis 1: Connection pool saturation, confidence: 0.85.\nSuggestion: increase pool size to 50.");

        when(promptManager.interpolate(eq("system"), anyMap())).thenReturn("system prompt");
        when(promptManager.interpolate(eq("planning"), anyMap())).thenReturn("planning prompt");
        when(promptManager.interpolate(eq("analysis"), anyMap())).thenReturn("analysis prompt");
        when(promptManager.interpolate(eq("report"), anyMap())).thenReturn("report prompt");
    }

    @Test
    @DisplayName("happy path: CREATED -> PLANNING -> QUERYING -> ANALYZING -> COMPLETED")
    void happyPath() {
        var ctx = new DiagnosisContext(conversation, message, application, llmProvider,
                List.of(prometheusDs), "Why is service slow?");
        when(evidenceRepo.findByMessageOrderByCollectedAtAsc(any())).thenReturn(List.of());
        when(evidenceRepo.save(any(Evidence.class))).thenAnswer(i -> i.getArgument(0));

        orchestrator.execute(ctx, ssePublisher);

        assertEquals("COMPLETED", message.getDiagnosisState());
        assertNotNull(message.getCompletedAt());
        assertLinesMatch(List.of(
                "state:PLANNING",
                "step:Analyzing question and planning diagnostic queries...",
                "state:QUERYING",
                "step:Querying PROMETHEUS: metrics...",
                "state:ANALYZING",
                "step:Analyzing collected evidence...",
                "state:COMPLETED",
                "done",
                "close"
        ), sseEvents);
        verify(evidenceRepo, atLeastOnce()).save(any(Evidence.class));
        verify(hypothesisRepo, atLeastOnce()).save(any(RootCauseHypothesis.class));
        verify(em, atLeastOnce()).merge(any(Message.class));
    }

    @Test
    @DisplayName("no data sources: skips queries, COMPLETED_PARTIAL")
    void noDataSourcesCompletesPartial() {
        var ctx = new DiagnosisContext(conversation, message, application, llmProvider,
                List.of(), "Why?");
        when(evidenceRepo.findByMessageOrderByCollectedAtAsc(any())).thenReturn(List.of());

        orchestrator.execute(ctx, ssePublisher);

        assertEquals("COMPLETED_PARTIAL", message.getDiagnosisState());
        assertTrue(sseEvents.contains("state:COMPLETED_PARTIAL"));
    }

    @Test
    @DisplayName("LLM throws: FAILED with safe error message")
    void llmFailureLeadsToFailed() {
        var ctx = new DiagnosisContext(conversation, message, application, llmProvider,
                List.of(prometheusDs), "Why?");
        when(llmClient.call(anyString(), anyString(), any(LlmProvider.class)))
                .thenThrow(new RuntimeException("LLM timeout"));

        orchestrator.execute(ctx, ssePublisher);

        assertEquals("FAILED", message.getDiagnosisState());
        assertNotNull(message.getFailureReason());
        assertTrue(message.getFailureReason().contains("internal error"));
        assertTrue(sseEvents.contains("state:FAILED"));
    }

    @Test
    @DisplayName("all connector queries fail: COMPLETED_PARTIAL, generic error in evidence")
    void allConnectorsFailPartial() {
        Connector failing = mock(Connector.class);
        when(failing.query(any())).thenThrow(new RuntimeException("Connection refused"));
        when(connectorRegistry.getConnector("PROMETHEUS")).thenReturn(failing);

        var ctx = new DiagnosisContext(conversation, message, application, llmProvider,
                List.of(prometheusDs), "Why?");
        when(evidenceRepo.findByMessageOrderByCollectedAtAsc(any())).thenReturn(List.of());
        when(evidenceRepo.save(any(Evidence.class))).thenAnswer(i -> i.getArgument(0));

        orchestrator.execute(ctx, ssePublisher);

        assertEquals("COMPLETED_PARTIAL", message.getDiagnosisState());
        verify(evidenceRepo, atLeastOnce()).save(any(Evidence.class));
    }

    @Test
    @DisplayName("null SSE publisher defaults to NOOP")
    void nullSsePublisher() {
        var ctx = new DiagnosisContext(conversation, message, application, llmProvider,
                List.of(), "Why?");
        when(evidenceRepo.findByMessageOrderByCollectedAtAsc(any())).thenReturn(List.of());

        assertDoesNotThrow(() -> orchestrator.execute(ctx, (DiagnosisSseEventPublisher) null));
    }

    @Test
    @DisplayName("DiagnosisContext validates required fields")
    void contextValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiagnosisContext(null, message, application, llmProvider, List.of(), "q"));
        assertThrows(IllegalArgumentException.class,
                () -> new DiagnosisContext(conversation, null, application, llmProvider, List.of(), "q"));
        assertThrows(IllegalArgumentException.class,
                () -> new DiagnosisContext(conversation, message, application, null, List.of(), "q"));
        assertThrows(IllegalArgumentException.class,
                () -> new DiagnosisContext(conversation, message, application, llmProvider, List.of(), ""));
    }

    @Test
    @DisplayName("multiple data sources: one fails, one succeeds -> COMPLETED")
    void multipleDataSourcesPartialFailure() {
        DataSource esDs = new DataSource();
        esDs.setType("ELASTICSEARCH");
        esDs.setName("logs");
        esDs.setUrl("http://es:9200");

        Connector promOk = mock(Connector.class);
        when(promOk.query(any())).thenReturn(new QueryResult() {});
        when(connectorRegistry.getConnector("PROMETHEUS")).thenReturn(promOk);

        Connector esFail = mock(Connector.class);
        when(esFail.query(any())).thenThrow(new RuntimeException("timeout"));
        when(connectorRegistry.getConnector("ELASTICSEARCH")).thenReturn(esFail);

        var ctx = new DiagnosisContext(conversation, message, application, llmProvider,
                List.of(prometheusDs, esDs), "Why?");
        when(evidenceRepo.findByMessageOrderByCollectedAtAsc(any())).thenReturn(List.of());
        when(evidenceRepo.save(any(Evidence.class))).thenAnswer(i -> i.getArgument(0));

        orchestrator.execute(ctx, ssePublisher);

        // One success → COMPLETED (not partial)
        assertEquals("COMPLETED", message.getDiagnosisState());
        // Both success and failure evidence persisted
        verify(evidenceRepo, atLeast(2)).save(any(Evidence.class));
    }

    @Test
    @DisplayName("parseHypotheses with empty analysis returns fallback")
    void parseHypothesesEmptyAnalysis() {
        var ctx = new DiagnosisContext(conversation, message, application, llmProvider,
                List.of(), "q");
        when(evidenceRepo.findByMessageOrderByCollectedAtAsc(any())).thenReturn(List.of());
        when(llmClient.call(anyString(), any(LlmProvider.class))).thenReturn("Everything is fine, nothing to see.");

        orchestrator.execute(ctx, ssePublisher);

        // fallback hypothesis with confidence 0.0 should be saved
        verify(hypothesisRepo).save(argThat(h ->
                h.getRank() == 1 && h.getConfidence() != null && h.getConfidence() == 0.0));
    }

    @Test
    @DisplayName("invalid state transition leads to ABORTED")
    void invalidStateTransitionAborts() {
        message.setDiagnosisState("COMPLETED"); // terminal state
        var ctx = new DiagnosisContext(conversation, message, application, llmProvider,
                List.of(), "q");

        orchestrator.execute(ctx, ssePublisher);

        assertEquals("ABORTED", message.getDiagnosisState());
        assertTrue(sseEvents.contains("state:ABORTED"));
    }

    private static void setEntityId(Object entity, UUID id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
