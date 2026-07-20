package io.epiphaneia.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record LlmProviderRequest(
        @NotBlank(message = "LLM提供商不能为空")
        String provider,

        @NotBlank(message = "模型名称不能为空")
        String modelName,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String apiKey,

        String baseUrl
) {
}
