package io.github.jianfeitu.stackarium.plugin;

import io.github.jianfeitu.stackarium.common.ApiException;
import io.github.jianfeitu.stackarium.component.ComponentDefinition;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import io.github.jianfeitu.stackarium.topology.TopologyNode;

@Component
public class PluginRegistry {
    private final Map<String, ComponentPlugin> plugins;

    public PluginRegistry(List<ComponentPlugin> plugins) {
        this.plugins = plugins.stream().collect(Collectors.toUnmodifiableMap(
                plugin -> plugin.definition().type(), Function.identity()));
    }

    public ComponentDefinition get(String type) {
        return plugin(type).definition();
    }

    public RuntimeContribution runtime(TopologyNode node) {
        return runtime(node, List.of());
    }

    public RuntimeContribution runtime(TopologyNode node, List<RuntimeConnection> connections) {
        Optional<RuntimeContribution> contribution = plugin(node.componentType()).runtime(node, connections);
        return contribution.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                "RUNTIME_UNSUPPORTED", "组件暂不支持运行：" + node.displayName()));
    }

    private ComponentPlugin plugin(String type) {
        ComponentPlugin plugin = plugins.get(type);
        if (plugin == null) throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_COMPONENT", "未知组件类型：" + type);
        return plugin;
    }

    public List<ComponentDefinition> list() {
        return plugins.values().stream().map(ComponentPlugin::definition)
                .sorted(Comparator.comparing(ComponentDefinition::name)).toList();
    }
}
