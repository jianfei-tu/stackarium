package io.github.jianfeitu.stackarium.assistant;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.jianfeitu.stackarium.common.ApiException;
import io.github.jianfeitu.stackarium.plugin.PluginRegistry;
import io.github.jianfeitu.stackarium.runtime.RuntimeService;
import io.github.jianfeitu.stackarium.topology.RelationType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

public class ArchitectureTools {
    private final UUID projectId;
    private final UUID conversationId;
    private final ArchitectureContextService contexts;
    private final PluginRegistry plugins;
    private final RuntimeService runtimes;
    private final ProposalService proposals;
    private final ObjectMapper json;
    private final List<String> executed = new ArrayList<>();
    private final List<UUID> createdProposals = new ArrayList<>();

    public ArchitectureTools(UUID projectId, UUID conversationId, ArchitectureContextService contexts,
                             PluginRegistry plugins, RuntimeService runtimes, ProposalService proposals, ObjectMapper json) {
        this.projectId = projectId;
        this.conversationId = conversationId;
        this.contexts = contexts;
        this.plugins = plugins;
        this.runtimes = runtimes;
        this.proposals = proposals;
        this.json = json;
    }

    public List<String> executed() { return List.copyOf(executed); }
    public List<UUID> createdProposals() { return List.copyOf(createdProposals); }

    @Tool("读取当前项目真实拓扑。解释架构、识别节点或判断连接是否存在时使用。")
    public String getTopology() {
        executed.add("getTopology");
        var context = contexts.build(projectId);
        return json.writeValueAsString(Map.of("projectId", context.projectId(), "projectName", context.projectName(),
                "revision", context.revision(), "nodes", context.nodes(), "edges", context.edges()));
    }

    @Tool("查询平台真实组件目录、组件能力、配置字段、允许的连接和 Runtime 支持情况。")
    public String getComponentCatalog() {
        executed.add("getComponentCatalog");
        return json.writeValueAsString(contexts.build(projectId).catalog());
    }

    @Tool("查询当前项目真实 Runtime 状态和组件健康。回答哪些组件正在运行时必须调用；没有 Runtime 时明确说明。")
    public String getRuntimeStatus() {
        executed.add("getRuntimeStatus");
        var runtime = runtimes.latest(projectId);
        return runtime.map(view -> json.writeValueAsString(Map.of("runtimeId", view.id(), "status", view.status(),
                "components", view.components().stream().map(component -> Map.of("nodeId", component.nodeId(),
                        "state", component.state(), "health", component.health())).toList())))
                .orElse("当前项目没有 Runtime");
    }

    @Tool("提出新增组件建议，不会立即修改架构。需要时可同时建议从现有节点建立连接；已有 Redis 时会优先复用。")
    public String addComponent(@P("目录中的组件类型，如 redis") String componentType,
                               @P(value = "节点显示名", required = false) String displayName,
                               @P(value = "非敏感配置键值，可省略", required = false) Map<String, String> config,
                               @P(value = "需要连接的现有源节点 ID，可省略", required = false) String connectedFromNodeId,
                               @P(value = "连接类型，如 CACHE，可省略", required = false) String relationType) {
        executed.add("addComponent");
        return suggest(() -> proposals.add(projectId, conversationId, componentType, displayName, config,
                optionalId(connectedFromNodeId), optionalRelation(relationType)));
    }

    @Tool("提出删除当前项目组件的建议，不会立即删除。")
    public String removeComponent(@P("当前拓扑中的节点 ID") String nodeId) {
        executed.add("removeComponent");
        return suggest(() -> proposals.remove(projectId, conversationId, UUID.fromString(nodeId)));
    }

    @Tool("提出连接两个现有组件的建议，不会立即连接。已存在时不会重复添加。")
    public String connectComponents(@P("源节点 ID") String sourceNodeId, @P("目标节点 ID") String targetNodeId,
                                    @P("目录允许的连接类型") String relationType) {
        executed.add("connectComponents");
        return suggest(() -> proposals.connect(projectId, conversationId, UUID.fromString(sourceNodeId),
                UUID.fromString(targetNodeId), RelationType.valueOf(relationType)));
    }

    @Tool("提出断开现有连接的建议，不会立即断开。")
    public String disconnectComponents(@P("源节点 ID") String sourceNodeId, @P("目标节点 ID") String targetNodeId,
                                       @P("连接类型") String relationType) {
        executed.add("disconnectComponents");
        return suggest(() -> proposals.disconnect(projectId, conversationId, UUID.fromString(sourceNodeId),
                UUID.fromString(targetNodeId), RelationType.valueOf(relationType)));
    }

    @Tool("提出修改组件允许配置项的建议，服务端会校验配置 schema；不会立即修改。")
    public String updateComponentConfig(@P("节点 ID") String nodeId, @P("允许的配置字段") String key,
                                        @P("新配置值") String value) {
        executed.add("updateComponentConfig");
        return suggest(() -> proposals.updateConfig(projectId, conversationId, UUID.fromString(nodeId), key, value));
    }

    @Tool("通过平台已有的拓扑验证、Runtime Plan 和 Compose Generator 生成当前项目运行配置。不会启动容器。")
    public String generateRuntimeConfig() {
        executed.add("generateRuntimeConfig");
        try {
            var view = runtimes.generate(projectId);
            return "已由平台生成 Runtime 配置；runtimeId=" + view.id() + "；topologyRevision="
                    + view.topologyRevision() + "；status=" + view.status();
        } catch (ApiException error) {
            return "平台验证失败：" + error.getMessage();
        }
    }

    private String suggest(ProposalAction action) {
        try {
            ChangeProposal proposal = action.create();
            createdProposals.add(proposal.id());
            return "已创建待确认建议：proposalId=" + proposal.id() + "；" + proposal.summary()
                    + "。用户确认前架构不会变化。";
        } catch (ApiException | IllegalArgumentException error) {
            return "无法创建建议：" + Secrets.redact(error.getMessage());
        }
    }

    private static UUID optionalId(String value) { return value == null || value.isBlank() ? null : UUID.fromString(value); }
    private static RelationType optionalRelation(String value) {
        return value == null || value.isBlank() ? null : RelationType.valueOf(value);
    }
    @FunctionalInterface private interface ProposalAction { ChangeProposal create(); }
}
