package io.epiphaneia.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record DataSourceRequest(
        @NotBlank(message = "数据源类型不能为空")
        String type,

        @NotBlank(message = "数据源名称不能为空")
        String name,

        @NotBlank(message = "数据源URL不能为空")
        String url,

        String authType,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String authConfig
) {
}
