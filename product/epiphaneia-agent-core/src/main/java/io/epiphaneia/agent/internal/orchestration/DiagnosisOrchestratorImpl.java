package io.epiphaneia.agent.internal.orchestration;

import io.epiphaneia.agent.api.DiagnosisContext;
import io.epiphaneia.agent.api.DiagnosisOrchestrator;
import io.epiphaneia.agent.api.orchestration.DiagnosisStateMachine;
import io.epiphaneia.llm.api.DiagnosisSseEventPublisher;
import io.epiphaneia.domain.entity.*;
import io.epiphaneia.domain.repository.*;
import io.epiphaneia.llm.api.client.LlmClient;
import io.epiphaneia.llm.api.routing.ModelRouter;
import io.epiphaneia.llm.api.template.PromptTemplateManager;
import io.epiphaneia.engine.api.query.EsQueryBuilder;
import io.epiphaneia.engine.api.query.PrometheusQueryBuilder;
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
    private final PrometheusQueryBuilder prometheusQueryBuilder;
    private final EsQueryBuilder esQueryBuilder;
    private final EntityManager em;
    private final EvidenceRepository evidenceRepo;
    private final RootCauseHypothesisRepository hypothesisRepo;
    private final FixSuggestionRepository suggestionRepo;

    public DiagnosisOrchestratorImpl(LlmClient llmClient, PromptTemplateManager promptManager,
                                      ModelRouter modelRouter, ConnectorRegistry connectorRegistry,
                                      PrometheusQueryBuilder prometheusQueryBuilder,
                                      EsQueryBuilder esQueryBuilder,
                                      EntityManager em, EvidenceRepository evidenceRepo,
                                      RootCauseHypothesisRepository hypothesisRepo,
                                      FixSuggestionRepository suggestionRepo) {
        this.llmClient = llmClient;
        this.promptManager = promptManager;
        this.modelRouter = modelRouter;
        this.connectorRegistry = connectorRegistry;
        this.prometheusQueryBuilder = prometheusQueryBuilder;
        this.esQueryBuilder = esQueryBuilder;
        this.em = em;
        this.evidenceRepo = evidenceRepo;
        this.hypothesisRepo = hypothesisRepo;
        this.suggestionRepo = suggestionRepo;
    }

    /**
     * Execute the full diagnosis pipeline.
     *
     * @param ctx       the diagnosis context
     * @param publisher SSE event publisher (use {@link DiagnosisSseEventPublisher#NOOP} if none)
     */
    @Override
    @Transactional
    public void execute(DiagnosisContext ctx, DiagnosisSseEventPublisher publisher) {
        if (publisher == null) publisher = DiagnosisSseEventPublisher.NOOP;

        Instant start = Instant.now();
        UUID msgId = ctx.message().getId();

        // Ensure diagnosis starts in CREATED state
        if (ctx.message().getDiagnosisState() == null) {
            ctx.message().setDiagnosisState(DiagnosisStateMachine.State.CREATED.name());
        }

        // Validate LLM provider
        modelRouter.validateProvider(ctx.llmProvider().getProvider());

        try {
            // 1. PLANNING
            checkTimeout(ctx, start);
            transition(ctx, DiagnosisStateMachine.State.PLANNING, publisher);
            planningPhase(ctx, publisher);

            // 2. QUERYING
            checkTimeout(ctx, start);
            transition(ctx, DiagnosisStateMachine.State.QUERYING, publisher);
            int queriesSucceeded = queryingPhase(ctx, publisher);

            // 3. ANALYZING
            checkTimeout(ctx, start);
            transition(ctx, DiagnosisStateMachine.State.ANALYZING, publisher);
            analyzingPhase(ctx, publisher);

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
            DiagnosisStateMachine.State current;
            try {
                current = parseState(ctx.message().getDiagnosisState());
            } catch (IllegalStateException corrupt) {
                // corrupt persisted state: FAILED is the safe terminal state
                current = null;
            }
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
        log.debug("Planning result for {}: length={} chars", ctx.message().getId(),
                plan != null ? plan.length() : 0);
        return plan;
    }

    // ─── Phase: Querying ────────────────────────────────────────────

    private int queryingPhase(DiagnosisContext ctx, DiagnosisSseEventPublisher pub) {
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
                List<Evidence> evidenceList = queryDataSource(ctx, ds);
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
    private List<Evidence> queryDataSource(DiagnosisContext ctx, DataSource ds) {
        String queryStr = buildQueryForDataSource(ds, ctx.question());
        if (queryStr == null || queryStr.isBlank()) {
            // fail loudly: the calling phase records a Failure evidence and continues
            throw new IllegalStateException(
                    "No query could be built for data source type " + ds.getType());
        }
        Connector connector = connectorRegistry.getConnector(ds.getType());
        if (connector == null) {
            throw new IllegalStateException("No connector registered for type " + ds.getType());
        }
        QueryResult result = connector.query(new QueryRequest.Typed(queryStr, ds.getUrl()));

        Evidence ev = new Evidence();
        ev.setMessage(ctx.message());
        ev.setSource(ds.getType());
        ev.setQueryText(queryStr);

        if (result instanceof QueryResult.Success s) {
            ev.setSummary(s.summary() != null ? s.summary() : "Data collected (" + ds.getType() + ")");
        } else if (result instanceof QueryResult.Failure f) {
            ev.setSummary("Query failed: " + f.error() + " — " + f.detail());
        } else {
            ev.setSummary("Response received (" + ds.getType() + ")");
        }
        return List.of(ev);
    }

    /** Build a reasonable default query for each data source type. */
    private String buildQueryForDataSource(DataSource ds, String question) {
        try {
            return switch (ds.getType()) {
                case "PROMETHEUS" -> prometheusQueryBuilder.buildInstantQuery("up", Map.of());
                case "ELASTICSEARCH" -> esQueryBuilder.buildSearchQuery(
                        extractSearchTerm(question), null, null, 50);
                default -> "";
            };
        } catch (Exception e) {
            // preserve the cause: the caller logs the failure evidence with full context
            throw new IllegalStateException(
                    "Failed to build query for data source type " + ds.getType(), e);
        }
    }

    /** Extract a simple search term from the user's question. */
    private static String extractSearchTerm(String question) {
        if (question == null || question.isBlank()) return "*";
        // ponytail: take first 100 chars, strip quotes
        return question.replace("\"", "").replace("'", "")
                .substring(0, Math.min(100, question.length()));
    }

    // ─── Phase: Analyzing ───────────────────────────────────────────

    private void analyzingPhase(DiagnosisContext ctx, DiagnosisSseEventPublisher pub) {
        pub.step(ctx.conversation().getId(), ctx.message().getId(),
                "Analyzing collected evidence...");

        List<Evidence> collected = evidenceRepo.findByMessageOrderByCollectedAtAsc(ctx.message());
        String evidenceText = formatEvidence(collected);

        String analysisPrompt = promptManager.interpolate("analysis", Map.of(
                "question", ctx.question(),
                "evidence", evidenceText));

        String analysis = llmClient.call(analysisPrompt, ctx.llmProvider());
        log.debug("Analysis result for {}: length={} chars", ctx.message().getId(),
                analysis != null ? analysis.length() : 0);

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

    private void checkTimeout(DiagnosisContext ctx, Instant start) {
        Instant createdAt = ctx.message().getCreatedAt() != null ? ctx.message().getCreatedAt() : start;
        DiagnosisStateMachine.State current;
        try {
            current = parseState(ctx.message().getDiagnosisState());
        } catch (IllegalStateException e) {
            // corrupt persisted state; let transition/abort handle it
            return;
        }
        if (DiagnosisStateMachine.isTimedOut(current, createdAt, Instant.now())) {
            throw new DiagnosisAbortedException(
                    "Diagnosis timed out (limit: " + DiagnosisStateMachine.timeoutDescription() + ")");
        }
    }

    private void transition(DiagnosisContext ctx, DiagnosisStateMachine.State newState,
                            DiagnosisSseEventPublisher pub) {
        String currentStr = ctx.message().getDiagnosisState();
        if (currentStr == null) {
            currentStr = DiagnosisStateMachine.State.CREATED.name();
            ctx.message().setDiagnosisState(currentStr);
        }
        DiagnosisStateMachine.State current;
        try {
            current = parseState(currentStr);
        } catch (IllegalStateException e) {
            // corrupt persisted state: abort instead of silently resetting to CREATED
            // (a reset would bypass transition validation and restart a failed diagnosis)
            throw new DiagnosisAbortedException("Corrupt diagnosis state '" + currentStr + "' in DB");
        }
        if (!DiagnosisStateMachine.isValidTransition(current, newState)) {
            throw new DiagnosisAbortedException(
                    "Invalid transition: " + current + " → " + newState);
        }
        ctx.message().setDiagnosisState(newState.name());
        em.merge(ctx.message());
        pub.state(ctx.conversation().getId(), ctx.message().getId(), newState.name());
    }

    private static DiagnosisStateMachine.State parseState(String s) {
        if (s == null) return DiagnosisStateMachine.State.CREATED;
        try {
            return DiagnosisStateMachine.State.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Unknown diagnosis state in DB: '" + s + "'", e);
        }
    }

    // ─── Parsers ────────────────────────────────────────────────────

    private void parseHypotheses(Message message, String analysis) {
        // Split on '1. ' or 'Hypothesis 1:' patterns with line anchors
        if (analysis == null) {
            saveFallbackHypothesis(message);
            return;
        }
        String[] parts = analysis.split("(?m)(?=^\\d+\\.\\s|^Hypothesis\\s\\d+)");
        short rank = 1;
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isBlank() || !looksLikeHypothesis(trimmed)) continue;
            RootCauseHypothesis h = new RootCauseHypothesis();
            h.setMessage(message);
            h.setRank(rank);
            String desc = trimmed.lines().findFirst().filter(s -> !s.isBlank())
                    .orElse("unknown");
            h.setDescription(desc.substring(0, Math.min(500, desc.length())));
            h.setConfidence(extractConfidence(trimmed));
            if (rank == 1 && h.getConfidence() == null) h.setConfidence(0.7);
            if (rank == 2 && h.getConfidence() == null) h.setConfidence(0.4);
            if (rank == 3 && h.getConfidence() == null) h.setConfidence(0.2);
            hypothesisRepo.save(h);
            if (rank++ >= 3) break;
        }
        if (rank == 1) {
            saveFallbackHypothesis(message);
        }
    }

    private void saveFallbackHypothesis(Message message) {
        RootCauseHypothesis fallback = new RootCauseHypothesis();
        fallback.setMessage(message);
        fallback.setRank((short) 1);
        fallback.setDescription("Unable to determine root cause from analysis.");
        fallback.setConfidence(0.0);
        hypothesisRepo.save(fallback);
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

    private static final int MAX_SUGGESTIONS = 10;

    private void parseSuggestions(Message message, String analysis) {
        // ponytail: simple extraction — "Suggestion:" / "Fix:" / numbered list in analysis
        if (analysis == null) return;
        String[] lines = analysis.split("\\n");
        int saved = 0;
        for (String line : lines) {
            if (saved >= MAX_SUGGESTIONS) break;
            String trimmed = line.trim();
            if (trimmed.matches("(?i)^(suggestion|fix|recommendation)\\s*[:\\-].*")
                    || trimmed.matches("^\\d+\\.\\s+(use|try|increase|decrease|set|update|restart|check|add|remove|adjust|reduce|configure).*")) {
                FixSuggestion s = new FixSuggestion();
                s.setMessage(message);
                s.setDescription(trimmed.substring(0, Math.min(500, trimmed.length())));
                s.setAutoExecutionAllowed(false);
                suggestionRepo.save(s);
                saved++;
            }
        }
    }

    private static final java.util.regex.Pattern CONFIDENCE_PATTERN =
            java.util.regex.Pattern.compile("(?i)confidence\\s*[:=]?\\s*(\\d+(?:\\.\\d+)?)");
    private static final java.util.regex.Pattern PERCENT_PATTERN =
            java.util.regex.Pattern.compile("(\\d+)%");
    private static final java.util.regex.Pattern WORD_HIGH_PATTERN =
            java.util.regex.Pattern.compile("(?i)\\bhigh\\b");

    private void parseRiskAssessment(Message message, String analysis) {
        if (analysis == null) return;
        String lower = analysis.toLowerCase();
        if (lower.contains("critical") || lower.contains("severe")) {
            message.setRiskLevel("CRITICAL");
        } else if (WORD_HIGH_PATTERN.matcher(lower).find()) {
            // word boundary: "highlight"/"slightly" must not match "high"
            message.setRiskLevel("HIGH");
        } else if (lower.contains("medium") || lower.contains("moderate")) {
            message.setRiskLevel("MEDIUM");
        } else {
            message.setRiskLevel("LOW");
        }
    }

    private static Double extractConfidence(String text) {
        var m = CONFIDENCE_PATTERN.matcher(text);
        if (m.find()) {
            try {
                double d = Double.parseDouble(m.group(1));
                return d > 1.0 ? d / 100.0 : d;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        var m2 = PERCENT_PATTERN.matcher(text);
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
