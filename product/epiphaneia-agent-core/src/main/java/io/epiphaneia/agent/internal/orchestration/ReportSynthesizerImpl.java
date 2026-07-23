package io.epiphaneia.agent.internal.orchestration;

import io.epiphaneia.agent.api.ReportSynthesizer;
import io.epiphaneia.domain.internal.entity.*;
import io.epiphaneia.domain.internal.repository.EvidenceRepository;
import io.epiphaneia.domain.internal.repository.FixSuggestionRepository;
import io.epiphaneia.domain.internal.repository.RootCauseHypothesisRepository;
import io.epiphaneia.llm.internal.client.LlmClient;
import io.epiphaneia.llm.internal.template.PromptTemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Synthesizes a structured Markdown diagnostic report from conversation data.
 * <p>
 * Uses the LLM to format evidence, hypotheses, and suggestions into a
 * human-readable report. Falls back to a template-only approach if the
 * LLM call fails.
 */
@Service
public class ReportSynthesizerImpl implements ReportSynthesizer {

    private static final Logger log = LoggerFactory.getLogger(ReportSynthesizerImpl.class);

    private final LlmClient llmClient;
    private final PromptTemplateManager promptManager;
    private final EvidenceRepository evidenceRepo;
    private final RootCauseHypothesisRepository hypothesisRepo;
    private final FixSuggestionRepository suggestionRepo;

    public ReportSynthesizerImpl(LlmClient llmClient, PromptTemplateManager promptManager,
                                  EvidenceRepository evidenceRepo,
                                  RootCauseHypothesisRepository hypothesisRepo,
                                  FixSuggestionRepository suggestionRepo) {
        this.llmClient = llmClient;
        this.promptManager = promptManager;
        this.evidenceRepo = evidenceRepo;
        this.hypothesisRepo = hypothesisRepo;
        this.suggestionRepo = suggestionRepo;
    }

    @Override
    public String synthesize(Conversation conversation) {
        List<Message> messages = conversation.getMessages();
        if (messages == null || messages.isEmpty()) {
            return "No diagnostic data available.";
        }

        // Find the latest AGENT message with diagnosis results
        Message diagnosis = messages.stream()
                .filter(m -> "AGENT".equals(m.getRole()))
                .reduce((first, second) -> second)
                .orElse(null);

        if (diagnosis == null) {
            return "No diagnosis results available yet.";
        }

        List<Evidence> evidence = evidenceRepo.findByMessageOrderByCollectedAtAsc(diagnosis);
        List<RootCauseHypothesis> hypotheses = hypothesisRepo.findByMessageOrderByRankAsc(diagnosis);
        List<FixSuggestion> suggestions = suggestionRepo.findByMessage(diagnosis);

        Application app = conversation.getApplication();

        try {
            String reportPrompt = promptManager.interpolate("report", Map.of(
                    "applicationName", app != null ? app.getName() : "unknown",
                    "question", messages.get(0).getContent(),
                    "duration", formatDuration(messages.get(0).getCreatedAt(), diagnosis.getCompletedAt()),
                    "hypotheses", formatHypotheses(hypotheses),
                    "evidence", formatEvidence(evidence),
                    "suggestions", formatSuggestions(suggestions),
                    "riskLevel", diagnosis.getRiskLevel() != null ? diagnosis.getRiskLevel() : "UNKNOWN",
                    "riskImpact", diagnosis.getRiskImpact() != null ? diagnosis.getRiskImpact() : "Not assessed",
                    "riskUrgency", diagnosis.getRiskUrgency() != null ? diagnosis.getRiskUrgency() : "Not assessed"
            ));

            return llmClient.call(reportPrompt);
        } catch (Exception e) {
            log.warn("LLM report synthesis failed, generating template-only report", e);
            return templateReport(app, messages.get(0), evidence, hypotheses, suggestions, diagnosis);
        }
    }

    // ─── Fallback template report (no LLM needed) ─────────────────────

    private String templateReport(Application app, Message question,
                                   List<Evidence> evidence, List<RootCauseHypothesis> hypotheses,
                                   List<FixSuggestion> suggestions, Message diagnosis) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Diagnostic Report\n\n");
        sb.append("## Summary\n");
        sb.append("Application: ").append(app != null ? app.getName() : "unknown").append("\n");
        sb.append("Question: ").append(question.getContent()).append("\n");
        sb.append("Status: ").append(diagnosis.getDiagnosisState()).append("\n\n");

        sb.append("## Evidence Collected\n");
        if (evidence.isEmpty()) {
            sb.append("No evidence collected.\n");
        } else {
            for (Evidence ev : evidence) {
                sb.append("- **").append(ev.getSource()).append("**: ")
                        .append(ev.getSummary()).append("\n");
            }
        }

        sb.append("\n## Root Cause Analysis\n");
        if (hypotheses.isEmpty()) {
            sb.append("No hypotheses generated.\n");
        } else {
            for (RootCauseHypothesis h : hypotheses) {
                sb.append("### Hypothesis ").append(h.getRank())
                        .append(" (Confidence: ").append(String.format("%.0f%%", h.getConfidence() * 100)).append(")\n");
                sb.append(h.getDescription()).append("\n\n");
            }
        }

        sb.append("## Recommendations\n");
        if (suggestions.isEmpty()) {
            sb.append("No specific recommendations.\n");
        } else {
            for (int i = 0; i < suggestions.size(); i++) {
                sb.append(i + 1).append(". ").append(suggestions.get(i).getDescription()).append("\n");
            }
        }

        sb.append("\n## Risk Assessment\n");
        sb.append("- Level: ").append(diagnosis.getRiskLevel() != null ? diagnosis.getRiskLevel() : "N/A").append("\n");
        sb.append("- Impact: ").append(diagnosis.getRiskImpact() != null ? diagnosis.getRiskImpact() : "N/A").append("\n");
        sb.append("- Urgency: ").append(diagnosis.getRiskUrgency() != null ? diagnosis.getRiskUrgency() : "N/A").append("\n");

        return sb.toString();
    }

    // ─── Formatters ──────────────────────────────────────────────────

    private String formatDuration(Instant start, Instant end) {
        if (start == null || end == null) return "unknown";
        Duration d = Duration.between(start, end);
        return d.toSeconds() + "s";
    }

    private String formatHypotheses(List<RootCauseHypothesis> hypotheses) {
        if (hypotheses == null || hypotheses.isEmpty()) return "No hypotheses.";
        return hypotheses.stream()
                .map(h -> String.format("#%d (%.0f%%): %s", h.getRank(),
                        h.getConfidence() != null ? h.getConfidence() * 100 : 0, h.getDescription()))
                .collect(Collectors.joining("\n"));
    }

    private String formatEvidence(List<Evidence> evidence) {
        if (evidence == null || evidence.isEmpty()) return "No evidence.";
        return evidence.stream()
                .map(e -> String.format("[%s] %s: %s", e.getSource(), e.getQueryText(), e.getSummary()))
                .collect(Collectors.joining("\n"));
    }

    private String formatSuggestions(List<FixSuggestion> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) return "No suggestions.";
        return suggestions.stream()
                .map(s -> "- " + s.getDescription())
                .collect(Collectors.joining("\n"));
    }
}
