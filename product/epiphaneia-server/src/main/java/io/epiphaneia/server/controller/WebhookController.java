package io.epiphaneia.server.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    @PostMapping("/alertmanager")
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    public Map<String, Object> alertmanager() {
        return Map.of("error", Map.of(
                "code", "NOT_IMPLEMENTED",
                "message", "AlertManager webhook integration is planned for v0.95"));
    }
}
