package io.epiphaneia.server.dto;

import java.util.UUID;

public record ProbeResponse(
        UUID id,
        boolean healthy,
        String info
) {
}
