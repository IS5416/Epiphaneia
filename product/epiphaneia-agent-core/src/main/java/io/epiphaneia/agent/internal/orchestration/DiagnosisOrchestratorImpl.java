package io.epiphaneia.agent.internal.orchestration;

import io.epiphaneia.agent.api.DiagnosisOrchestrator;
import io.epiphaneia.agent.api.DiagnosisSseEventPublisher;
import io.epiphaneia.agent.api.model.*;
import io.epiphaneia.agent.api.repository.*;
import io.epiphaneia.agent.internal.llm.LlmClient;
import io.epiphaneia.agent.internal.llm.ModelRouter;
import io.epiphaneia.agent.internal.llm.PromptTemplateManager;
import io.epiphaneia.infra.api.ConnectorRegistry;
import io.epiphaneia.infra.api.connector.Connector;
import io.epiphaneia.infra.api.connector.QueryRequest;
import io.epiphaneia.infra.api.connector.QueryResult;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.*;

/**
 * Implements the diagnostic ReAct loop: Planning → Querying → Analyzing → Completed/Partial/Failed/Aborted.
 * <p>
 * State transitions are validated by {@link DiagnosisStateMachine}. SSE events are emitted
 * via the publisher passed in the execution context. Thread-safe: no mutable instance state.
 */
@Service
public class DiagnosisOrchestratorImpl implements DiagnosisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisOrchestratorImpl.class);

    private final LlmClient llmClient;
    private final PromptTemplateManager promptManager;
    private final ModelRouter modelRouter;
    private final ConnectorRegistry connectorRegistry;
    private final EntityManager em;
    private final EvidenceRepository evidenceRepo;
    private final RootCauseHypothesisRepository hypothesisRepo;
    private final FixSuggestionRepository suggestionRepo;

    public DiagnosisOrchestratorImpl(LlmClient llmClient, PromptTemplateManager promptManager,
                                      ModelRouter modelRouter, ConnectorRegistry connectorRegistry,
                                      EntityManager em, EvidenceRepository evidenceRepo,
                                      RootCauseHypothesisRepository hypothesisRepo,
                                      FixSuggestionRepository suggestionRepo) {
        this.llmClient = llmClient;
        this.promptManager = promptManager;
        this.modelRouter = modelRouter;
        this.connectorRegistry = connectorRegistry;
        this.em = em;
        this.evidenceRepo = evidenceRepo;
        this.hypothesisRepo = hypothesisRepo;
        this.suggestionRepo = suggestionRepo;
    }

    @Override
    @Transactional
    public Object execute(Object conversation, String question) {
        throw new UnsupportedOperationException("Use execute(DiagnosisContext, DiagnosisSseEventPublisher)");
    }

    /**
     * Execute the full diagnosis pipeline.
     *
     * @param ctx       the diagnosis context
     * @param publisher SSE event publisher (use {@link DiagnosisSseEventPublisher#NOOP} if none)
     */
    public void execute(DiagnosisContext ctx, DiagnosisSseEventPublisher publisher) {
        if (publisher == null) publisher = DiagnosisSseEventPublisher.NOOP;

        Instant start = Instant.now();
        UUID convId = ctx.conversation().getId();
        UUID msgId = ctx.message().getId();

        // Ensure diagnosis starts in CREATED state
        if (ctx.message().getDiagnosisState() == null) {
            ctx.message().setDiagnosisState(DiagnosisStateMachine.State.CREATED.name());
        }

        // Validate LLM provider
        modelRouter.validateProvider(ctx.llmProvider().getProvider());

        try {
            // 1. PLANNING
            transition(ctx, DiagnosisStateMachine.State.PLANNING, publisher);
            String plan = planningPhase(ctx, publisher);

            // 2. QUERYING
            transition(ctx, DiagnosisStateMachine.State.QUERYING, publisher);
            int queriesSucceeded = queryingPhase(ctx, plan, publisher);

            // 3. ANALYZING
            transition(ctx, DiagnosisStateMachine.State.ANALYZING, publisher);
            analyzingPhase(ctx, plan, publisher);

            // 4. COMPLETED or COMPLETED_PARTIAL
            if (queriesSucceeded > 0) {
                complete(ctx, start, DiagnosisStateMachine.State.COMPLETED, publisher);
            } else {
                complete(ctx, start, DiagnosisStateMachine.State.COMPLETED_PARTIAL, publisher);
            }

        } catch (DiagnosisAbortedException e) {
            abort(ctx, e.getMessage(), publisher);
        } catch (Exception e) {
            log.error("Diagnosis failed for message {}", msgId, e);
            DiagnosisStateMachine.State current = parseState(ctx.message().getDiagnosisState());
            if (current == DiagnosisStateMachine.State.QUERYING) {
                complete(ctx, start, DiagnosisStateMachine.State.COMPLETED_PARTIAL, publisher);
            } else {
                fail(ctx, "An internal error occurred during diagnosis.", publisher);
            }
        }
    }

    // ─── Phase: Planning ────────────────────────────────────────────

    private String planningPhase(DiagnosisContext ctx, DiagnosisSseEventPublisher pub) {
        pub.step(ctx.conversation().getId(), ctx.message().getId(),
                "Analyzing question and planning diagnostic queries...");

        String systemPrompt = promptManager.interpolate("system", Map.of(
                "applicationName", ctx.application() != null ? ctx.application().getName() : "unknown",
                "dataSources", describeDataSources(ctx.dataSources())));

        String planningPrompt = promptManager.interpolate("planning", Map.of(
                "question", ctx.question(),
                "dataSourceDetails", describeDataSourceDetails(ctx.dataSources())));

        String plan = llmClient.call(systemPrompt, planningPrompt, ctx.llmProvider());
        log.debug("Planning result for {}: length={} chars", ctx.message().getId(), plan.length());
        return plan;
    }

    // ─── Phase: Querying ────────────────────────────────────────────

    private int queryingPhase(DiagnosisContext ctx, String plan, DiagnosisSseEventPublisher pub) {
        List<DataSource> sources = ctx.dataSources() != null ? ctx.dataSources() : List.of();
        if (sources.isEmpty()) {
            pub.step(ctx.conversation().getId(), ctx.message().getId(),
                    "No data sources available — skipping query phase");
            return 0;
        }

        int succeeded = 0;
        for (DataSource ds : sources) {
            try {
                pub.step(ctx.conversation().getId(), ctx.message().getId(),
                        "Querying " + ds.getType() + ": " + ds.getName() + "...");
                List<Evidence> evidenceList = queryDataSource(ctx, ds, plan);
                for (Evidence ev : evidenceList) {
                    evidenceRepo.save(ev);
                }
                succeeded++;
            } catch (Exception e) {
                log.warn("Query failed for data source {}: {}", ds.getName(), e.getMessage());
                Evidence failEvidence = new Evidence();
                failEvidence.setMessage(ctx.message());
                failEvidence.setSource(ds.getType());
                failEvidence.setQueryText("(query failed)");
                failEvidence.setSummary("Data source unavailable: " + ds.getType() + " could not be queried.");
                evidenceRepo.save(failEvidence);
            }
        }

        if (succeeded == 0 && !sources.isEmpty()) {
            pub.step(ctx.conversation().getId(), ctx.message().getId(),
                    "All data source queries failed");
        }
        return succeeded;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Evidence> queryDataSource(DiagnosisContext ctx, DataSource ds, String plan) {
        Connector connector = connectorRegistry.getConnector(ds.getType());
        QueryResult result = connector.query(new QueryRequest() {
            @Override public String toString() { return "plan"; }
        });

        Evidence ev = new Evidence();
        ev.setMessage(ctx.message());
        ev.setSource(ds.getType());
        ev.setQueryText("Queried " + ds.getType() + " at " + sanitizeUrl(ds.getUrl()));
        ev.setSummary("Response: " + (result != null ? "data collected" : "empty"));
        return List.of(ev);
    }

    // ─── Phase: Analyzing ───────────────────────────────────────────

    private void analyzingPhase(DiagnosisContext ctx, String plan, DiagnosisSseEventPublisher pub) {
        pub.step(ctx.conversation().getId(), ctx.message().getId(),
                "Analyzing collected evidence...");

        List<Evidence> collected = evidenceRepo.findByMessageOrderByCollectedAtAsc(ctx.message());
        String evidenceText = formatEvidence(collected);

        String analysisPrompt = promptManager.interpolate("analysis", Map.of(
                "question", ctx.question(),
                "evidence", evidenceText));

        String analysis = llmClient.call(analysisPrompt, ctx.llmProvider());
        log.debug("Analysis result for {}: length={} chars", ctx.message().getId(), analysis.length());

        parseHypotheses(ctx.message(), analysis);
        parseSuggestions(ctx.message(), analysis);
        parseRiskAssessment(ctx.message(), analysis);
    }

    // ─── Terminal states ────────────────────────────────────────────

    private void complete(DiagnosisContext ctx, Instant start,
                          DiagnosisStateMachine.State state, DiagnosisSseEventPublisher pub) {
        ctx.message().setDiagnosisState(state.name());
        ctx.message().setCompletedAt(Instant.now());
        em.merge(ctx.message());

        pub.state(ctx.conversation().getId(), ctx.message().getId(), state.name());
        pub.done(ctx.conversation().getId(), ctx.message().getId());
        pub.close(ctx.conversation().getId());
    }

    private void abort(DiagnosisContext ctx, String reason, DiagnosisSseEventPublisher pub) {
        ctx.message().setDiagnosisState(DiagnosisStateMachine.State.ABORTED.name());
        ctx.message().setFailureReason(reason);
        ctx.message().setCompletedAt(Instant.now());
        em.merge(ctx.message());

        pub.state(ctx.conversation().getId(), ctx.message().getId(),
                DiagnosisStateMachine.State.ABORTED.name());
        pub.error(ctx.conversation().getId(), ctx.message().getId(), reason);
        pub.close(ctx.conversation().getId());
    }

    private void fail(DiagnosisContext ctx, String reason, DiagnosisSseEventPublisher pub) {
        ctx.message().setDiagnosisState(DiagnosisStateMachine.State.FAILED.name());
        ctx.message().setFailureReason(reason);
        ctx.message().setCompletedAt(Instant.now());
        em.merge(ctx.message());

        pub.state(ctx.conversation().getId(), ctx.message().getId(),
                DiagnosisStateMachine.State.FAILED.name());
        pub.error(ctx.conversation().getId(), ctx.message().getId(), reason);
        pub.close(ctx.conversation().getId());
    }

    // ─── State management ───────────────────────────────────────────

    private void transition(DiagnosisContext ctx, DiagnosisStateMachine.State newState,
                            DiagnosisSseEventPublisher pub) {
        String currentStr = ctx.message().getDiagnosisState();
        if (currentStr == null) {
            currentStr = DiagnosisStateMachine.State.CREATED.name();
            ctx.message().setDiagnosisState(currentStr);
        }
        DiagnosisStateMachine.State current = parseState(currentStr);
        if (!DiagnosisStateMachine.isValidTransition(current, newState)) {
            throw new DiagnosisAbortedException(
                    "Invalid transition: " + current + " → " + newState);
        }
        ctx.message().setDiagnosisState(newState.name());
        em.merge(ctx.message());
        pub.state(ctx.conversation().getId(), ctx.message().getId(), newState.name());
    }

    private static DiagnosisStateMachine.State parseState(String s) {
        try {
            return DiagnosisStateMachine.State.valueOf(s);
        } catch (Exception e) {
            return DiagnosisStateMachine.State.CREATED;
        }
    }

    // ─── Parsers ────────────────────────────────────────────────────

    private void parseHypotheses(Message message, String analysis) {
        // Split on '1. ' or 'Hypothesis 1:' patterns with line anchors
        String[] parts = analysis.split("(?m)(?=^\\d+\\.\\s|^Hypothesis\\s\\d+)");
        short rank = 1;
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isBlank() || !looksLikeHypothesis(trimmed)) continue;
            RootCauseHypothesis h = new RootCauseHypothesis();
            h.setMessage(message);
            h.setRank(rank);
            String desc = trimmed.lines().findFirst().orElse("unknown");
            h.setDescription(desc.substring(0, Math.min(500, desc.length())));
            h.setConfidence(extractConfidence(trimmed));
            if (rank == 1 && h.getConfidence() == null) h.setConfidence(0.7);
            if (rank == 2 && h.getConfidence() == null) h.setConfidence(0.4);
            if (rank == 3 && h.getConfidence() == null) h.setConfidence(0.2);
            hypothesisRepo.save(h);
            if (rank++ >= 3) break;
        }
        if (rank == 1) {
            RootCauseHypothesis fallback = new RootCauseHypothesis();
            fallback.setMessage(message);
            fallback.setRank((short) 1);
            fallback.setDescription("Unable to determine root cause from analysis.");
            fallback.setConfidence(0.0);
            hypothesisRepo.save(fallback);
        }
    }

    /** Heuristic: does this text segment look like a hypothesis rather than generic commentary? */
    private static boolean looksLikeHypothesis(String text) {
        String lower = text.toLowerCase();
        return lower.contains("cause") || lower.contains("error") || lower.contains("latency")
                || lower.contains("issue") || lower.contains("problem") || lower.contains("fail")
                || lower.contains("bottleneck") || lower.contains("memory") || lower.contains("cpu")
                || lower.contains("disk") || lower.contains("network") || lower.contains("timeout")
                || lower.contains("saturation") || lower.contains("exhaust") || lower.contains("leak")
                || lower.contains("crash") || lower.contains("overload") || lower.contains("degrad")
                || extractConfidence(text) != null;
    }

    private void parseSuggestions(Message message, String analysis) {
        // ponytail: simple extraction — "Suggestion:" / "Fix:" / numbered list in analysis
        String[] lines = analysis.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches("(?i)^(suggestion|fix|recommendation)\\s*[:\\-].*")
                    || trimmed.matches("^\\d+\\.\\s+(use|try|increase|decrease|set|update|restart|check|add|remove|adjust|reduce|configure).*")) {
                FixSuggestion s = new FixSuggestion();
                s.setMessage(message);
                s.setDescription(trimmed.substring(0, Math.min(500, trimmed.length())));
                s.setAutoExecutionAllowed(false);
                suggestionRepo.save(s);
            }
        }
    }

    private void parseRiskAssessment(Message message, String analysis) {
        String lower = analysis.toLowerCase();
        if (lower.contains("critical") || lower.contains("severe")) {
            message.setRiskLevel("CRITICAL");
        } else if (lower.contains("high")) {
            message.setRiskLevel("HIGH");
        } else if (lower.contains("medium") || lower.contains("moderate")) {
            message.setRiskLevel("MEDIUM");
        } else {
            message.setRiskLevel("LOW");
        }
    }

    private static Double extractConfidence(String text) {
        var m = java.util.regex.Pattern.compile(
                "(?i)confidence\\s*[:=]?\\s*(\\d+(?:\\.\\d+)?)").matcher(text);
        if (m.find()) {
            try {
                double d = Double.parseDouble(m.group(1));
                return d > 1.0 ? d / 100.0 : d;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        var m2 = java.util.regex.Pattern.compile("(\\d+)%").matcher(text);
        if (m2.find()) {
            return Double.parseDouble(m2.group(1)) / 100.0;
        }
        return null;
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private String describeDataSources(List<DataSource> sources) {
        if (sources == null || sources.isEmpty()) return "none";
        return sources.stream()
                .map(ds -> ds.getType() + " (" + sanitizePromptValue(ds.getName()) + ")")
                .reduce((a, b) -> a + ", " + b).orElse("none");
    }

    private String describeDataSourceDetails(List<DataSource> sources) {
        if (sources == null || sources.isEmpty()) return "No data sources configured.";
        StringBuilder sb = new StringBuilder();
        for (DataSource ds : sources) {
            sb.append("- ").append(ds.getType()).append(": ")
                    .append(sanitizePromptValue(ds.getName()))
                    .append(" at ").append(sanitizeUrl(ds.getUrl())).append("\n");
        }
        return sb.toString();
    }

    private String formatEvidence(List<Evidence> evidence) {
        if (evidence == null || evidence.isEmpty()) return "No evidence collected.";
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Evidence ev : evidence) {
            sb.append("Evidence #").append(i++).append(":\n");
            sb.append("  Source: ").append(ev.getSource()).append("\n");
            sb.append("  Query: ").append(ev.getQueryText()).append("\n");
            sb.append("  Summary: ").append(ev.getSummary()).append("\n\n");
        }
        return sb.toString();
    }

    private static String sanitizePromptValue(String value) {
        if (value == null) return "unknown";
        return value.replace("\n", " ").replace("\r", "").substring(0, Math.min(value.length(), 100));
    }

    private static String sanitizeUrl(String url) {
        if (url == null) return "(unknown)";
        try {
            URI uri = new URI(url);
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                    uri.getPath(), null, null).toString();
        } catch (Exception e) {
            return "(invalid url)";
        }
    }

    /** Internal exception to signal an ABORTED transition. */
    static class DiagnosisAbortedException extends RuntimeException {
        DiagnosisAbortedException(String msg) { super(msg); }
    }
}
