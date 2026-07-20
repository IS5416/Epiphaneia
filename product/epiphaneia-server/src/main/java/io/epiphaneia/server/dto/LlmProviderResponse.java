package io.epiphaneia.server.dto;

import java.time.Instant;
import java.util.UUID;

public record LlmProviderResponse(
        UUID id,
        String provider,
        String modelName,
        String baseUrl,
        boolean connected,
        Instant updatedAt
) {
}
