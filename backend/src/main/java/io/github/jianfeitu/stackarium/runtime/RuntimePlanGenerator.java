package io.github.jianfeitu.stackarium.runtime;

import io.github.jianfeitu.stackarium.common.ApiException;
import io.github.jianfeitu.stackarium.plugin.PluginRegistry;
import io.github.jianfeitu.stackarium.plugin.RuntimeContribution;
import io.github.jianfeitu.stackarium.plugin.RuntimeConnection;
import io.github.jianfeitu.stackarium.topology.Topology;
import io.github.jianfeitu.stackarium.topology.TopologyEdge;
import io.github.jianfeitu.stackarium.topology.TopologyNode;
import io.github.jianfeitu.stackarium.topology.TopologyValidator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RuntimePlanGenerator {
    private final PluginRegistry plugins;
    private final TopologyValidator validator;

    public RuntimePlanGenerator(PluginRegistry plugins, TopologyValidator validator) {
        this.plugins = plugins;
        this.validator = validator;
    }

    public RuntimePlan generate(Topology topology) {
        validator.validate(topology.nodes(), topology.edges());
        if (topology.nodes().isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,
                "EMPTY_RUNTIME", "请先在架构中添加可运行组件并保存");
        Map<UUID, String> names = topology.nodes().stream().collect(Collectors.toMap(
                TopologyNode::id, node -> "n" + node.id().toString().replace("-", "")));
        List<RuntimePlan.Service> services = topology.nodes().stream().map(node -> {
            List<RuntimeConnection> connections = topology.edges().stream()
                    .filter(edge -> edge.source().equals(node.id()))
                    .map(edge -> new RuntimeConnection(edge.relationType(),
                            topology.nodes().stream().filter(target -> target.id().equals(edge.target()))
                                    .findFirst().orElseThrow(), names.get(edge.target())))
                    .toList();
            RuntimeContribution contribution = plugins.runtime(node, connections);
            List<String> dependencies = topology.edges().stream()
                    .filter(edge -> edge.source().equals(node.id()))
                    .map(TopologyEdge::target).map(names::get).distinct().toList();
            return new RuntimePlan.Service(node.id(), node.componentType(), node.displayName(),
                    names.get(node.id()), contribution.image(), contribution.environment(),
                    contribution.ports(), contribution.volumes(), dependencies,
                    contribution.healthcheck(), contribution.startPeriodSeconds(), contribution.files(),
                    contribution.buildModule(), contribution.publishedPort());
        }).toList();
        return new RuntimePlan(topology.projectId(), topology.revision(), List.of("stackarium"), services);
    }
}
