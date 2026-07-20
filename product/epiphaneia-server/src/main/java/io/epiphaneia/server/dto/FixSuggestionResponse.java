package io.epiphaneia.server.dto;

import java.util.UUID;

public record FixSuggestionResponse(
        UUID id,
        String description,
        boolean autoExecutionAllowed
) {
}
