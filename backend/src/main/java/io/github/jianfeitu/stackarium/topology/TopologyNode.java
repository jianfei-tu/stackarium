package io.github.jianfeitu.stackarium.topology;

import java.util.Map;
import java.util.UUID;

public record TopologyNode(UUID id, String componentType, String displayName,
                           double x, double y, Map<String, String> config) {}
