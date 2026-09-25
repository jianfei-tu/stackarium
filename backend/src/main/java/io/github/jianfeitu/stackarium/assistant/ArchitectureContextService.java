package io.github.jianfeitu.stackarium.assistant;

import io.github.jianfeitu.stackarium.component.ComponentDefinition;
import io.github.jianfeitu.stackarium.plugin.PluginRegistry;
import io.github.jianfeitu.stackarium.project.Project;
import io.github.jianfeitu.stackarium.project.ProjectService;
import io.github.jianfeitu.stackarium.runtime.RuntimeService;
import io.github.jianfeitu.stackarium.runtime.RuntimeView;
import io.github.jianfeitu.stackarium.topology.Topology;
import io.github.jianfeitu.stackarium.topology.TopologyService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ArchitectureContextService {
    public record Node(UUID nodeId, String componentType, String displayName, Map<String, String> config) {}
    public record Edge(UUID sourceNodeId, UUID targetNodeId, String relationType) {}
    public record CatalogItem(String componentType, String name, String capability,
                              List<ConfigField> configFields,
                              List<ComponentDefinition.ConnectionRule> connections, boolean runtimeAvailable) {}
    public record ConfigField(String key, String kind, boolean required) {}
    public record Health(UUID nodeId, String state, String health) {}
    public record Runtime(UUID runtimeId, String status, List<Health> components) {}
    public record Context(UUID projectId, String projectName, long revision, List<Node> nodes,
                          List<Edge> edges, List<CatalogItem> catalog, Runtime runtime) {}

    private final ProjectService projects;
    private final TopologyService topologies;
    private final PluginRegistry plugins;
    private final RuntimeService runtimes;

    public ArchitectureContextService(ProjectService projects, TopologyService topologies,
                                      PluginRegistry plugins, RuntimeService runtimes) {
        this.projects = projects;
        this.topologies = topologies;
        this.plugins = plugins;
        this.runtimes = runtimes;
    }

    public Context build(UUID projectId) {
        Project project = projects.get(projectId);
        Topology topology = topologies.get(projectId);
        RuntimeView runtime = runtimes.latest(projectId).orElse(null);
        return new Context(project.id(), project.name(), topology.revision(),
                topology.nodes().stream().map(node -> new Node(node.id(), node.componentType(),
                        node.displayName(), safeConfig(node.config()))).toList(),
                topology.edges().stream().map(edge -> new Edge(edge.source(), edge.target(),
                        edge.relationType().name())).toList(),
                plugins.list().stream().map(item -> new CatalogItem(item.type(), item.name(),
                        item.description(), item.configFields().stream()
                        .filter(field -> !Secrets.sensitiveKey(field.key()))
                        .map(field -> new ConfigField(field.key(), field.kind(), field.required())).toList(),
                        item.connections(), item.runtimeAvailable())).toList(),
                runtime == null ? null : new Runtime(runtime.id(), runtime.status().name(),
                        runtime.components().stream().map(component -> new Health(component.nodeId(),
                                component.state(), component.health())).toList()));
    }

    static Map<String, String> safeConfig(Map<String, String> config) {
        Map<String, String> safe = new LinkedHashMap<>();
        if (config == null) return safe;
        config.forEach((key, value) -> {
            if (!Secrets.sensitiveKey(key)) safe.put(key, Secrets.redact(value));
        });
        return safe;
    }
}
