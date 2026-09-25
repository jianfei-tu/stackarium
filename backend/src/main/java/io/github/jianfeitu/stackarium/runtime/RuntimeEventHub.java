package io.github.jianfeitu.stackarium.runtime;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

@Component
public class RuntimeEventHub extends TextWebSocketHandler {
    private final RuntimeRepository repository;
    private final ObjectMapper json;
    private final Map<UUID, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public RuntimeEventHub(RuntimeRepository repository, ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    public RuntimeEvent publish(UUID projectId, UUID runtimeId, String type, UUID nodeId,
                                Map<String, String> payload) {
        return publish(projectId, runtimeId, type, nodeId, payload, Instant.now());
    }

    public RuntimeEvent publish(UUID projectId, UUID runtimeId, String type, UUID nodeId,
                                Map<String, String> payload, Instant occurredAt) {
        RuntimeEvent event = new RuntimeEvent(UUID.randomUUID(), runtimeId, occurredAt, type, nodeId, payload);
        repository.event(event);
        String message = json.writeValueAsString(event);
        for (WebSocketSession session : sessions.getOrDefault(projectId, Set.of())) {
            try {
                synchronized (session) { if (session.isOpen()) session.sendMessage(new TextMessage(message)); }
            } catch (IOException ignored) { sessions.getOrDefault(projectId, Set.of()).remove(session); }
        }
        return event;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID projectId = projectId(session.getUri());
        if (projectId == null) { session.close(CloseStatus.BAD_DATA); return; }
        sessions.computeIfAbsent(projectId, ignored -> new CopyOnWriteArraySet<>()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID projectId = projectId(session.getUri());
        if (projectId != null) sessions.getOrDefault(projectId, Set.of()).remove(session);
    }

    private static UUID projectId(URI uri) {
        if (uri == null) return null;
        String[] parts = uri.getPath().split("/");
        if (parts.length != 5 || !parts[1].equals("ws") || !parts[2].equals("projects")
                || !parts[4].equals("runtime")) return null;
        try { return UUID.fromString(parts[3]); } catch (IllegalArgumentException error) { return null; }
    }
}
