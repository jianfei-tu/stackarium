package io.github.jianfeitu.stackarium.component;

import io.github.jianfeitu.stackarium.topology.RelationType;
import java.util.List;

public record ComponentDefinition(String type, String name, String category, String description,
                                  List<ConfigField> configFields, List<ConnectionRule> connections,
                                  boolean runtimeAvailable) {
    public record ConfigField(String key, String label, String kind, String defaultValue, boolean required) {}
    public record ConnectionRule(RelationType relation, List<String> targetTypes) {}
}
