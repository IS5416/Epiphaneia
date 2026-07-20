package io.epiphaneia.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateConversationRequest(
        @NotNull(message = "应用ID不能为空")
        UUID applicationId,

        @NotBlank(message = "会话标题不能为空")
        String title,

        @NotBlank(message = "问题不能为空")
        String question
) {
}
