package io.epiphaneia.server.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateApiTokenRequest(@NotBlank String name) {}
