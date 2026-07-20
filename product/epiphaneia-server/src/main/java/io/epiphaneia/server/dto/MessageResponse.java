package io.epiphaneia.server.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        String role,
        String content,
        String diagnosisState,
        String failureReason,
        String riskLevel,
        String riskImpact,
        String riskUrgency,
        Integer tokenCount,
        Instant createdAt,
        Instant completedAt,
        List<EvidenceResponse> evidence,
        List<RootCauseHypothesisResponse> hypotheses,
        List<FixSuggestionResponse> suggestions
) {
}
