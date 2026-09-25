package io.github.jianfeitu.stackarium.topology;

import java.util.UUID;

public record TopologyEdge(UUID id, UUID source, UUID target, RelationType relationType) {}
