package io.epiphaneia.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "当前密码不能为空")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String currentPassword,

        @NotBlank(message = "新密码不能为空")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String newPassword
) {
}
