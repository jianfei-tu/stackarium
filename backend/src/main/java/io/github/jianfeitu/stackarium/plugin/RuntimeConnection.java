package io.github.jianfeitu.stackarium.plugin;

import io.github.jianfeitu.stackarium.topology.RelationType;
import io.github.jianfeitu.stackarium.topology.TopologyNode;

public record RuntimeConnection(RelationType relation, TopologyNode target, String serviceName) {}
