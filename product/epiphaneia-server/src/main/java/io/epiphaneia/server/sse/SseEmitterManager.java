package io.epiphaneia.server.sse;

import io.epiphaneia.llm.api.DiagnosisSseEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active SSE connections per conversation and implements
 * {@link DiagnosisSseEventPublisher} for the orchestrator.
 * <p>
 * Each SSE connection is identified by conversationId. Multiple clients
 * can connect to the same conversation (e.g., reconnect scenario).
 */
@Component
public class SseEmitterManager implements DiagnosisSseEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterManager.class);
    private static final long SSE_TIMEOUT = 120_000L;

    private final Map<UUID, Map<String, SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** Register a new SSE client for a conversation. */
    public SseEmitter createEmitter(UUID conversationId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String clientId = UUID.randomUUID().toString();

        emitter.onCompletion(() -> remove(conversationId, clientId));
        emitter.onTimeout(() -> remove(conversationId, clientId));
        emitter.onError(e -> remove(conversationId, clientId));

        emitters.computeIfAbsent(conversationId, k -> new ConcurrentHashMap<>())
                .put(clientId, emitter);
        return emitter;
    }

    private void remove(UUID conversationId, String clientId) {
        Map<String, SseEmitter> clients = emitters.get(conversationId);
        if (clients != null) {
            clients.remove(clientId);
            if (clients.isEmpty()) {
                emitters.remove(conversationId);
            }
        }
    }

    /** Send an SSE event to all clients of a conversation. */
    private void send(UUID conversationId, String eventName, Object data) {
        Map<String, SseEmitter> clients = emitters.get(conversationId);
        if (clients == null) return;

        var deadClients = new java.util.ArrayList<String>();
        for (var entry : clients.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                deadClients.add(entry.getKey());
            }
        }
        deadClients.forEach(clients::remove);
        if (clients.isEmpty()) {
            emitters.remove(conversationId);
        }
    }

    @Override
    public void state(UUID convId, UUID msgId, String newState) {
        send(convId, "state", Map.of("conversationId", convId, "messageId", msgId, "state", newState));
    }

    @Override
    public void step(UUID convId, UUID msgId, String description) {
        send(convId, "step", Map.of("conversationId", convId, "messageId", msgId, "step", description));
    }

    @Override
    public void token(UUID convId, UUID msgId, String token) {
        send(convId, "token", Map.of("conversationId", convId, "messageId", msgId, "token", token));
    }

    @Override
    public void done(UUID convId, UUID msgId) {
        send(convId, "done", Map.of("conversationId", convId, "messageId", msgId, "state", "COMPLETED"));
    }

    @Override
    public void error(UUID convId, UUID msgId, String reason) {
        send(convId, "error", Map.of("conversationId", convId, "messageId", msgId, "error", reason));
    }

    @Override
    public void close(UUID convId) {
        send(convId, "close", Map.of("conversationId", convId));
        // Clean up all clients for this conversation
        Map<String, SseEmitter> clients = emitters.remove(convId);
        if (clients != null) {
            clients.values().forEach(SseEmitter::complete);
        }
    }
}
