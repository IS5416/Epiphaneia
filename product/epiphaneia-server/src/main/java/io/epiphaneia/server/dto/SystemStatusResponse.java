package io.epiphaneia.server.dto;

public record SystemStatusResponse(
        boolean bootstrapped,
        boolean adminExists,
        boolean llmConfigured,
        long dataSourcesCount,
        long applicationsCount
) {
}
