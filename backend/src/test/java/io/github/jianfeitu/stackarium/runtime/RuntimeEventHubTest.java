package io.github.jianfeitu.stackarium.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class RuntimeEventHubTest {
    @Test
    void persistedEventIsPushedWithStableEnvelopeAndNodeMapping() throws Exception {
        UUID project = UUID.randomUUID();
        UUID runtime = UUID.randomUUID();
        UUID node = UUID.randomUUID();
        RuntimeRepository repository = mock(RuntimeRepository.class);
        ObjectMapper json = new ObjectMapper();
        RuntimeEventHub hub = new RuntimeEventHub(repository, json);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/projects/" + project + "/runtime"));
        when(session.isOpen()).thenReturn(true);
        hub.afterConnectionEstablished(session);

        RuntimeEvent event = hub.publish(project, runtime, "component.status.changed", node,
                Map.of("state", "running", "health", "healthy"));

        ArgumentCaptor<RuntimeEvent> stored = ArgumentCaptor.forClass(RuntimeEvent.class);
        verify(repository).event(stored.capture());
        assertThat(stored.getValue()).isEqualTo(event);
        ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(message.capture());
        var envelope = json.readTree(message.getValue().getPayload());
        assertThat(envelope.get("eventId").asText()).isEqualTo(event.eventId().toString());
        assertThat(envelope.get("runtimeId").asText()).isEqualTo(runtime.toString());
        assertThat(envelope.get("nodeId").asText()).isEqualTo(node.toString());
        assertThat(envelope.get("eventType").asText()).isEqualTo("component.status.changed");
        assertThat(envelope.get("payload").get("health").asText()).isEqualTo("healthy");
    }
}
