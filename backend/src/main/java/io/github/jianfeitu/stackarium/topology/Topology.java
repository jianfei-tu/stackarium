package io.github.jianfeitu.stackarium.topology;

import java.util.List;
import java.util.UUID;

public record Topology(UUID projectId, long revision, List<TopologyNode> nodes, List<TopologyEdge> edges) {}
