package io.epiphaneia.server.dto;

public record LoginResponse(
        String token,
        String prefix,
        boolean mustChangePassword
) {
}
