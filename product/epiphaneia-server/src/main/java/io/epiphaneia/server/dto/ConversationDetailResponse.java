package io.epiphaneia.server.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationDetailResponse(
        UUID id,
        UUID applicationId,
        String applicationName,
        String title,
        Instant createdAt,
        Instant updatedAt,
        List<MessageResponse> messages
) {
}
