package io.epiphaneia.server.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record ApplicationRequest(
        @NotBlank(message = "应用名称不能为空")
        String name,

        String actuatorUrl,

        @NotBlank(message = "Prometheus标签不能为空")
        String prometheusLabel,

        String tags
) {
}
