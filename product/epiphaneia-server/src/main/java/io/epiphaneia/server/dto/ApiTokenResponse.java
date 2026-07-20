package io.epiphaneia.server.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiTokenResponse(
        UUID id,
        String name,
        String prefix,
        Instant createdAt
) {
}
