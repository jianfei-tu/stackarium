package io.github.jianfeitu.stackarium.component;

import io.github.jianfeitu.stackarium.plugin.ComponentPlugin;
import io.github.jianfeitu.stackarium.plugin.RuntimeContribution;
import io.github.jianfeitu.stackarium.topology.TopologyNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RedisPlugin implements ComponentPlugin {
    @Override
    public ComponentDefinition definition() {
        return new ComponentDefinition("redis", "Redis", "缓存", "键值缓存",
                List.of(new ComponentDefinition.ConfigField("port", "端口", "number", "6379", true)),
                List.of(), true);
    }

    @Override
    public Optional<RuntimeContribution> runtime(TopologyNode node) {
        RuntimePluginChecks.fixedPort(node, 6379);
        return Optional.of(new RuntimeContribution("redis:8.2.9", Map.of(), List.of(6379),
                Map.of("data", "/data"), List.of("CMD", "redis-cli", "ping"), 5, Map.of()));
    }
}
