package io.github.jianfeitu.stackarium.assistant;

import io.github.jianfeitu.stackarium.topology.RelationType;
import java.util.Map;
import java.util.UUID;

public record ChangeOperation(Type type, UUID nodeId, String componentType, String displayName,
                              Map<String, String> config, UUID sourceNodeId, UUID targetNodeId,
                              RelationType relationType, String configKey, String oldValue, String newValue) {
    public enum Type { ADD_NODE, REMOVE_NODE, CONNECT, DISCONNECT, UPDATE_CONFIG }
}
