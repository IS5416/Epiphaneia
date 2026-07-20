package io.epiphaneia.server.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateApiTokenResponse(UUID id, String name, String token, String prefix, Instant createdAt) {}
