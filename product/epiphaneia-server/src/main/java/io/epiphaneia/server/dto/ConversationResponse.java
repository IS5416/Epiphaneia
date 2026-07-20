package io.epiphaneia.server.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID applicationId,
        String applicationName,
        String title,
        Instant createdAt,
        Instant updatedAt,
        String lastMessage
) {
}
