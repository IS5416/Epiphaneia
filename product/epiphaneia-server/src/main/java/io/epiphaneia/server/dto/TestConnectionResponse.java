package io.epiphaneia.server.dto;

public record TestConnectionResponse(
        boolean success,
        String message
) {
}
