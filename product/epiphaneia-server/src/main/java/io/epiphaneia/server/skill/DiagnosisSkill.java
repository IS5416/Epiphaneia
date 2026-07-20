package io.epiphaneia.server.skill;

import io.epiphaneia.agent.api.DiagnosisSseEventPublisher;
import io.epiphaneia.agent.api.model.*;
import io.epiphaneia.agent.api.repository.*;
import io.epiphaneia.agent.internal.orchestration.DiagnosisContext;
import io.epiphaneia.agent.internal.orchestration.DiagnosisOrchestratorImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Transactional skill layer that orchestrates the full diagnosis flow.
 * <p>
 * Responsibilities:
 * <ol>
 *   <li>Validate inputs and look up required entities</li>
 *   <li>Create the AGENT message in CREATED state</li>
 *   <li>Build DiagnosisContext and invoke the orchestrator</li>
 *   <li>Manage the SSE publisher lifecycle</li>
 * </ol>
 * The @Transactional boundary ensures all persistence operations within
 * a single diagnosis are atomic.
 */
@Service
public class DiagnosisSkill {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisSkill.class);

    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;
    private final ApplicationRepository appRepo;
    private final DataSourceRepository dsRepo;
    private final LlmProviderRepository llmRepo;
    private final DiagnosisOrchestratorImpl orchestrator;

    public DiagnosisSkill(ConversationRepository conversationRepo, MessageRepository messageRepo,
                           ApplicationRepository appRepo, DataSourceRepository dsRepo,
                           LlmProviderRepository llmRepo, DiagnosisOrchestratorImpl orchestrator) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.appRepo = appRepo;
        this.dsRepo = dsRepo;
        this.llmRepo = llmRepo;
        this.orchestrator = orchestrator;
    }

    /**
     * Execute a diagnosis for a conversation.
     *
     * @param conversationId the target conversation
     * @param question       the user's diagnostic question
     * @param ssePublisher   SSE publisher for real-time streaming (null → NOOP)
     * @return the created AGENT message entity (diagnosis state updated in-place)
     */
    // ponytail: TransactionTemplate inside orchestrator handles per-entity persistence.
    // @Transactional removed because diagnosis now runs on virtual thread, not request thread.
    public Message diagnose(UUID conversationId, String question,
                            DiagnosisSseEventPublisher ssePublisher) {
        Conversation conversation = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        // Check for active diagnosis
        if (hasActiveDiagnosis(conversation)) {
            throw new IllegalStateException("A diagnosis is already in progress for this conversation");
        }

        Application application = conversation.getApplication();
        LlmProvider llmProvider = llmRepo.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No LLM provider configured"));

        // Data sources can be empty (valid scenario — COMPLETED_PARTIAL)
        List<DataSource> dataSources = dsRepo.findAll();

        // Create AGENT message
        Message agentMsg = new Message();
        agentMsg.setConversation(conversation);
        agentMsg.setRole("AGENT");
        agentMsg.setContent(question);
        agentMsg.setDiagnosisState("CREATED");
        messageRepo.save(agentMsg);

        // Build context
        DiagnosisContext ctx = new DiagnosisContext(
                conversation, agentMsg, application, llmProvider, dataSources, question);

        // Execute diagnosis
        log.info("Starting diagnosis for conversation {}: {}", conversationId, question);
        orchestrator.execute(ctx, ssePublisher != null ? ssePublisher : DiagnosisSseEventPublisher.NOOP);

        return agentMsg;
    }

    private boolean hasActiveDiagnosis(Conversation conversation) {
        List<Message> messages = messageRepo.findByConversationOrderByCreatedAtAsc(conversation);
        return messages.stream().anyMatch(m ->
                m.getDiagnosisState() != null && isActive(m.getDiagnosisState()));
    }

    private static boolean isActive(String state) {
        return state.equals("CREATED") || state.equals("PLANNING")
                || state.equals("QUERYING") || state.equals("ANALYZING");
    }
}
