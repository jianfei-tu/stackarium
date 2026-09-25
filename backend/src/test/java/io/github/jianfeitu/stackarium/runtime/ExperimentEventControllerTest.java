package io.github.jianfeitu.stackarium.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.jianfeitu.stackarium.common.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class ExperimentEventControllerTest {
    @TempDir Path temp;

    @Test
    void acceptsOnlyEventsForTheMatchingRuntimeAndNode() throws Exception {
        UUID project = UUID.randomUUID();
        UUID runtime = UUID.randomUUID();
        UUID node = UUID.randomUUID();
        RuntimeArtifacts artifacts = new RuntimeArtifacts(temp.toString());
        Path workspace = artifacts.create(project, runtime);
        Files.writeString(workspace.resolve(".env"), "STACKARIUM_EVENT_TOKEN=private-token\n");
        RuntimePlan.Service service = new RuntimePlan.Service(node, "gateway", "Gateway", "service",
                "image", Map.of(), List.of(8080), Map.of(), List.of(), List.of("CMD", "true"),
                5, Map.of(), "gateway-service", 18080);
        RuntimePlan plan = new RuntimePlan(project, 1, List.of("stackarium"), List.of(service));
        RuntimeInstance instance = new RuntimeInstance(runtime, project, 1, "stackarium-test",
                workspace.toString(), plan, RuntimeStatus.RUNNING, null, null, null,
                Instant.now(), null, null, Instant.now());
        RuntimeRepository repository = mock(RuntimeRepository.class);
        RuntimeEventHub hub = mock(RuntimeEventHub.class);
        when(repository.find(project, runtime)).thenReturn(Optional.of(instance));
        ExperimentEventController collector = new ExperimentEventController(repository, artifacts,
                hub, new ObjectMapper());
        Instant occurredAt = Instant.now();
        String body = "{\"nodeId\":\"" + node + "\",\"occurredAt\":\"" + occurredAt
                + "\",\"eventType\":\"REQUEST_TRACE\","
                + "\"payload\":{\"traceId\":\"0123456789abcdef0123456789abcdef\",\"stage\":\"gateway.received\"}}";

        assertThat(collector.ingest(project, runtime, "private-token", body).getStatusCode().value())
                .isEqualTo(202);
        verify(hub).publish(project, runtime, "REQUEST_TRACE", node,
                Map.of("traceId", "0123456789abcdef0123456789abcdef", "stage", "gateway.received"),
                occurredAt);
        assertThatThrownBy(() -> collector.ingest(project, runtime, "wrong-token", body))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> collector.ingest(project, runtime, "private-token",
                body.replace(node.toString(), UUID.randomUUID().toString())))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> collector.ingest(project, runtime, "private-token",
                body.replace("REQUEST_TRACE", "runtime.status.changed")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> collector.ingest(project, runtime, "private-token", "x".repeat(4097)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> collector.ingest(project, runtime, "private-token",
                body.replace(occurredAt.toString(), occurredAt.minusSeconds(180).toString())))
                .isInstanceOf(ApiException.class);
    }
}
