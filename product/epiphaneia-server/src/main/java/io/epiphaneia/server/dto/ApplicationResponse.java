package io.epiphaneia.server.dto;

import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        String name,
        String actuatorUrl,
        String prometheusLabel,
        String tags,
        String actuatorInfo,
        Instant createdAt
) {
}
