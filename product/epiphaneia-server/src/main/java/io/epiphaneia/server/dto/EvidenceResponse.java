package io.epiphaneia.server.dto;

import java.time.Instant;
import java.util.UUID;

public record EvidenceResponse(
        UUID id,
        String source,
        String queryText,
        String summary,
        Instant anomalyStart,
        Instant anomalyEnd,
        Instant collectedAt
) {
}
