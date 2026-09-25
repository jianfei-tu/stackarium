package io.github.jianfeitu.stackarium.runtime;

import io.github.jianfeitu.stackarium.common.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/runtime/{runtimeId}/experiment-events")
public class ExperimentEventController {
    public record Incoming(UUID nodeId, Instant occurredAt, String eventType, Map<String, String> payload) {}

    private static final Set<String> TYPES = Set.of("REQUEST_TRACE", "CACHE_ACCESS",
            "MESSAGE_PUBLISHED", "MESSAGE_CONSUMED", "SENTINEL_BLOCKED");
    private final RuntimeRepository repository;
    private final RuntimeArtifacts artifacts;
    private final RuntimeEventHub hub;
    private final ObjectMapper json;

    public ExperimentEventController(RuntimeRepository repository, RuntimeArtifacts artifacts,
                                     RuntimeEventHub hub, ObjectMapper json) {
        this.repository = repository;
        this.artifacts = artifacts;
        this.hub = hub;
        this.json = json;
    }

    @PostMapping
    public ResponseEntity<Void> ingest(@PathVariable UUID projectId, @PathVariable UUID runtimeId,
                                       @RequestHeader("X-Stackarium-Token") String token,
                                       @RequestBody String body) {
        if (body.getBytes(StandardCharsets.UTF_8).length > 4096) throw invalid("事件内容超过 4 KB");
        RuntimeInstance instance = repository.find(projectId, runtimeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "RUNTIME_NOT_FOUND", "运行环境不存在"));
        if (!Set.of(RuntimeStatus.PREPARING, RuntimeStatus.STARTING, RuntimeStatus.RUNNING,
                RuntimeStatus.DEGRADED).contains(instance.status())) throw invalid("运行环境未处于接收事件状态");
        verifyToken(instance, token);
        Incoming incoming;
        try { incoming = json.readValue(body, Incoming.class); }
        catch (Exception error) { throw invalid("事件 JSON 无效"); }
        if (incoming.nodeId() == null || incoming.occurredAt() == null
                || Duration.between(incoming.occurredAt(), Instant.now()).abs().compareTo(Duration.ofMinutes(2)) > 0
                || incoming.eventType() == null || !TYPES.contains(incoming.eventType())
                || incoming.payload() == null || incoming.payload().size() > 12)
            throw invalid("事件类型或结构无效");
        if (instance.plan().services().stream().noneMatch(service -> service.nodeId().equals(incoming.nodeId())
                && service.buildModule() != null))
            throw invalid("事件节点不属于当前运行环境");
        for (Map.Entry<String, String> item : incoming.payload().entrySet()) {
            if (!item.getKey().matches("[a-zA-Z][a-zA-Z0-9]{0,39}") || item.getValue() == null
                    || item.getValue().length() > 300) throw invalid("事件字段无效");
        }
        if (incoming.eventType().equals("REQUEST_TRACE")) {
            String traceId = incoming.payload().get("traceId");
            if (traceId == null || !traceId.matches("[a-f0-9]{32}")) throw invalid("请求事件缺少有效 traceId");
        }
        hub.publish(projectId, runtimeId, incoming.eventType(), incoming.nodeId(), incoming.payload(),
                incoming.occurredAt());
        return ResponseEntity.accepted().build();
    }

    private void verifyToken(RuntimeInstance instance, String token) {
        try {
            String expected = Files.readAllLines(artifacts.verified(instance).resolve(".env")).stream()
                    .filter(line -> line.startsWith("STACKARIUM_EVENT_TOKEN="))
                    .map(line -> line.substring("STACKARIUM_EVENT_TOKEN=".length()))
                    .findFirst().orElse("");
            if (expected.isBlank() || token.length() > 100 || !MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
                throw new ApiException(HttpStatus.FORBIDDEN, "EVENT_TOKEN_INVALID", "事件入口凭据无效");
        } catch (IOException error) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EVENT_TOKEN_UNAVAILABLE", "事件入口暂不可用");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EXPERIMENT_EVENT", message);
    }
}
