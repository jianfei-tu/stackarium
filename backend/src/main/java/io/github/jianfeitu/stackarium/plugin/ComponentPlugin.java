package io.github.jianfeitu.stackarium.plugin;

import io.github.jianfeitu.stackarium.component.ComponentDefinition;
import io.github.jianfeitu.stackarium.topology.TopologyNode;
import java.util.Optional;
import java.util.List;

public interface ComponentPlugin {
    ComponentDefinition definition();

    default Optional<RuntimeContribution> runtime(TopologyNode node) { return Optional.empty(); }
    default Optional<RuntimeContribution> runtime(TopologyNode node, List<RuntimeConnection> connections) {
        return runtime(node);
    }
}
