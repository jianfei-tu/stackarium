package io.github.jianfeitu.stackarium.runtime;

import io.github.jianfeitu.stackarium.runtime.DockerCommandExecutor.Operation;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DockerObserver {
    private final DockerCommandExecutor docker;
    private final ObjectMapper json;

    public DockerObserver(DockerCommandExecutor docker, ObjectMapper json) {
        this.docker = docker;
        this.json = json;
    }

    public List<ContainerObservation> observe(RuntimeInstance instance) throws IOException, InterruptedException {
        DockerCommandExecutor.Result result = docker.execute(instance, Operation.PS);
        if (!result.ok()) throw new IOException("读取容器状态失败：" + result.errorSummary());
        Map<String, JsonNode> byService = new HashMap<>();
        for (String line : result.stdout().lines().filter(value -> !value.isBlank()).toList()) {
            JsonNode parsed = json.readTree(line);
            if (parsed.isArray()) for (JsonNode item : parsed) byService.put(text(item, "Service"), item);
            else byService.put(text(parsed, "Service"), parsed);
        }
        Instant now = Instant.now();
        List<ContainerObservation> observations = new ArrayList<>();
        for (RuntimePlan.Service service : instance.plan().services()) {
            JsonNode container = byService.get(service.serviceName());
            observations.add(new ContainerObservation(service.nodeId(), service.componentType(),
                    service.displayName(), service.serviceName(), text(container, "ID"),
                    container == null ? "missing" : text(container, "State"),
                    container == null ? "none" : text(container, "Health"), now));
        }
        return observations;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) return "";
        return node.get(field).asText();
    }
}
