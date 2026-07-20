package io.epiphaneia.server.dto;

import java.time.Instant;
import java.util.UUID;

public record DataSourceResponse(
        UUID id,
        String type,
        String name,
        String url,
        String authType,
        boolean connected,
        String metadata,
        Instant createdAt,
        Instant updatedAt
) {
}
