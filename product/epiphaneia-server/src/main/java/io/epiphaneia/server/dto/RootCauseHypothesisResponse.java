package io.epiphaneia.server.dto;

import java.util.UUID;

public record RootCauseHypothesisResponse(
        UUID id,
        Short rank,
        String description,
        Double confidence,
        String supportingEvidenceIds
) {
}
