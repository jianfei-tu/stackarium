package io.github.jianfeitu.stackarium.topology;

import io.github.jianfeitu.stackarium.common.ApiException;
import io.github.jianfeitu.stackarium.component.ComponentDefinition;
import io.github.jianfeitu.stackarium.plugin.PluginRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TopologyValidator {
    private final PluginRegistry registry;

    public TopologyValidator(PluginRegistry registry) { this.registry = registry; }

    public void validate(List<TopologyNode> nodes, List<TopologyEdge> edges) {
        if (nodes == null || edges == null) fail("拓扑节点和连线不能为空");
        if (nodes.size() > 200 || edges.size() > 400) fail("当前项目超过第一阶段的拓扑规模限制");
        Map<UUID, TopologyNode> byId = new HashMap<>();
        for (TopologyNode node : nodes) {
            if (node == null || node.id() == null) fail("节点必须具有 ID");
            if (byId.putIfAbsent(node.id(), node) != null) fail("节点 ID 重复");
            if (node.displayName() == null || node.displayName().isBlank() || node.displayName().length() > 120)
                fail("节点名称不能为空且最多 120 字符");
            if (!Double.isFinite(node.x()) || !Double.isFinite(node.y()) || Math.abs(node.x()) > 100000 || Math.abs(node.y()) > 100000)
                fail("节点位置超出有效范围");
            ComponentDefinition definition = registry.get(node.componentType());
            validateConfig(node.config(), definition);
        }
        Set<UUID> edgeIds = new HashSet<>();
        Set<String> connections = new HashSet<>();
        for (TopologyEdge edge : edges) {
            if (edge == null || edge.id() == null || edge.source() == null || edge.target() == null || edge.relationType() == null)
                fail("连线信息不完整");
            if (!edgeIds.add(edge.id())) fail("连线 ID 重复");
            if (edge.source().equals(edge.target())) fail("节点不能连接自身");
            TopologyNode source = byId.get(edge.source());
            TopologyNode target = byId.get(edge.target());
            if (source == null || target == null) fail("连线两端必须属于当前拓扑");
            String key = edge.source() + ":" + edge.target() + ":" + edge.relationType();
            if (!connections.add(key)) fail("重复的组件连接");
            boolean allowed = registry.get(source.componentType()).connections().stream()
                    .anyMatch(rule -> rule.relation() == edge.relationType()
                            && rule.targetTypes().contains(target.componentType()));
            if (!allowed) fail("该组件组合不支持 " + edge.relationType() + " 连接");
        }
    }

    private void validateConfig(Map<String, String> config, ComponentDefinition definition) {
        if (config == null) fail("节点配置不能为空");
        Set<String> allowed = new HashSet<>();
        for (ComponentDefinition.ConfigField field : definition.configFields()) {
            allowed.add(field.key());
            String value = config.get(field.key());
            if (field.required() && (value == null || value.isBlank())) fail(field.label() + "不能为空");
            if (value != null && value.length() > 200) fail(field.label() + "最多 200 字符");
            if ("number".equals(field.kind()) && value != null) {
                try {
                    int port = Integer.parseInt(value);
                    if (port < 1 || port > 65535) fail(field.label() + "必须在 1 到 65535 之间");
                } catch (NumberFormatException error) {
                    fail(field.label() + "必须是有效端口");
                }
            }
        }
        if (!allowed.containsAll(config.keySet())) fail("节点配置包含未定义字段");
    }

    private static void fail(String message) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOPOLOGY", message);
    }
}
