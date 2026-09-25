package io.github.jianfeitu.stackarium.assistant;

import io.github.jianfeitu.stackarium.common.ApiException;
import io.github.jianfeitu.stackarium.component.ComponentDefinition;
import io.github.jianfeitu.stackarium.plugin.PluginRegistry;
import io.github.jianfeitu.stackarium.topology.RelationType;
import io.github.jianfeitu.stackarium.topology.Topology;
import io.github.jianfeitu.stackarium.topology.TopologyEdge;
import io.github.jianfeitu.stackarium.topology.TopologyNode;
import io.github.jianfeitu.stackarium.topology.TopologyService;
import io.github.jianfeitu.stackarium.topology.TopologyValidator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProposalService {
    private final AssistantRepository repository;
    private final TopologyService topologies;
    private final TopologyValidator validator;
    private final PluginRegistry plugins;

    public ProposalService(AssistantRepository repository, TopologyService topologies,
                           TopologyValidator validator, PluginRegistry plugins) {
        this.repository = repository;
        this.topologies = topologies;
        this.validator = validator;
        this.plugins = plugins;
    }

    public ChangeProposal add(UUID projectId, UUID conversationId, String componentType, String displayName,
                              Map<String, String> requestedConfig, UUID connectedFrom, RelationType relationType) {
        Topology current = topologies.get(projectId);
        ComponentDefinition definition = plugins.get(componentType);
        if (connectedFrom != null) requireNode(current, connectedFrom);
        // Reuse a current component where it makes sense; the caller can request another node explicitly later.
        if (connectedFrom != null && "redis".equals(componentType)) {
            for (TopologyNode node : current.nodes()) {
                if (node.componentType().equals(componentType)) {
                    if (hasEdge(current, connectedFrom, node.id(), relationType))
                        throw conflict("该缓存节点和连接已存在");
                    return connect(projectId, conversationId, connectedFrom, node.id(), relationType);
                }
            }
        }
        UUID nodeId = UUID.randomUUID();
        Map<String, String> config = new LinkedHashMap<>();
        definition.configFields().forEach(field -> config.put(field.key(), field.defaultValue()));
        if (requestedConfig != null) requestedConfig.forEach((key, value) -> {
            if (Secrets.sensitiveKey(key)) throw bad("不能设置敏感配置字段");
            config.put(key, value);
        });
        String name = displayName == null || displayName.isBlank() ? definition.name() : displayName.trim();
        List<ChangeOperation> changes = new ArrayList<>();
        changes.add(new ChangeOperation(ChangeOperation.Type.ADD_NODE, nodeId, componentType, name,
                config, null, null, null, null, null, null));
        if (connectedFrom != null) {
            if (relationType == null) throw bad("需要指定连接类型");
            changes.add(new ChangeOperation(ChangeOperation.Type.CONNECT, null, null, null,
                    null, connectedFrom, nodeId, relationType, null, null, null));
        }
        String summary = connectedFrom == null ? "新增 " + name : "新增 " + name + " 并建立 " + relationType + " 连接";
        return propose(projectId, conversationId, current, summary, changes);
    }

    public ChangeProposal remove(UUID projectId, UUID conversationId, UUID nodeId) {
        Topology current = topologies.get(projectId);
        TopologyNode node = requireNode(current, nodeId);
        return propose(projectId, conversationId, current, "删除 " + node.displayName(),
                List.of(new ChangeOperation(ChangeOperation.Type.REMOVE_NODE, nodeId, null, node.displayName(),
                        null, null, null, null, null, null, null)));
    }

    public ChangeProposal connect(UUID projectId, UUID conversationId, UUID source, UUID target, RelationType relation) {
        Topology current = topologies.get(projectId);
        requireNode(current, source);
        requireNode(current, target);
        if (relation == null) throw bad("需要指定连接类型");
        if (hasEdge(current, source, target, relation)) throw conflict("该连接已经存在，无需重复添加");
        return propose(projectId, conversationId, current, "建立 " + relation + " 连接",
                List.of(new ChangeOperation(ChangeOperation.Type.CONNECT, null, null, null,
                        null, source, target, relation, null, null, null)));
    }

    public ChangeProposal disconnect(UUID projectId, UUID conversationId, UUID source, UUID target, RelationType relation) {
        Topology current = topologies.get(projectId);
        if (!hasEdge(current, source, target, relation)) throw bad("当前架构中没有这条连接");
        return propose(projectId, conversationId, current, "断开 " + relation + " 连接",
                List.of(new ChangeOperation(ChangeOperation.Type.DISCONNECT, null, null, null,
                        null, source, target, relation, null, null, null)));
    }

    public ChangeProposal updateConfig(UUID projectId, UUID conversationId, UUID nodeId, String key, String value) {
        Topology current = topologies.get(projectId);
        TopologyNode node = requireNode(current, nodeId);
        if (key == null || Secrets.sensitiveKey(key)) throw bad("不能修改敏感配置字段");
        boolean allowed = plugins.get(node.componentType()).configFields().stream().anyMatch(field -> field.key().equals(key));
        if (!allowed) throw bad("该组件没有允许修改的配置项：" + key);
        if (value == null) throw bad("配置值不能为空");
        if (value.equals(node.config().get(key))) throw conflict("配置值没有变化");
        return propose(projectId, conversationId, current, "修改 " + node.displayName() + " 的 " + key,
                List.of(new ChangeOperation(ChangeOperation.Type.UPDATE_CONFIG, nodeId, null,
                        node.displayName(), null, null, null, null, key, node.config().get(key), value)));
    }

    private ChangeProposal propose(UUID projectId, UUID conversationId, Topology current,
                                   String summary, List<ChangeOperation> operations) {
        repository.conversation(projectId, conversationId);
        apply(current, operations); // validate before showing an actionable proposal
        ChangeProposal proposal = new ChangeProposal(UUID.randomUUID(), projectId, conversationId,
                current.revision(), summary, operations, ChangeProposal.Status.PENDING, Instant.now());
        repository.addProposal(proposal);
        return proposal;
    }

    @Transactional
    public Topology confirm(UUID projectId, UUID proposalId) {
        ChangeProposal proposal = repository.proposal(projectId, proposalId);
        if (proposal.status() != ChangeProposal.Status.PENDING) throw conflict("建议修改已处理");
        Topology current = topologies.get(projectId);
        if (current.revision() != proposal.expectedRevision()) {
            throw conflict("架构已变化，请重新生成建议");
        }
        Topology changed = apply(current, proposal.operations());
        Topology saved = topologies.save(projectId, proposal.expectedRevision(), changed.nodes(), changed.edges());
        if (!repository.updateProposalStatus(projectId, proposalId, ChangeProposal.Status.PENDING, ChangeProposal.Status.CONFIRMED))
            throw conflict("建议修改已处理");
        return saved;
    }

    public ChangeProposal cancel(UUID projectId, UUID proposalId) {
        ChangeProposal proposal = repository.proposal(projectId, proposalId);
        if (proposal.status() != ChangeProposal.Status.PENDING) throw conflict("建议修改已处理");
        if (!repository.updateProposalStatus(projectId, proposalId, ChangeProposal.Status.PENDING, ChangeProposal.Status.CANCELLED))
            throw conflict("建议修改已处理");
        return repository.proposal(projectId, proposalId);
    }

    Topology apply(Topology current, List<ChangeOperation> operations) {
        List<TopologyNode> nodes = new ArrayList<>(current.nodes());
        List<TopologyEdge> edges = new ArrayList<>(current.edges());
        for (ChangeOperation op : operations) {
            switch (op.type()) {
                case ADD_NODE -> {
                    double x = nodes.stream().mapToDouble(TopologyNode::x).max().orElse(0) + 270;
                    double y = nodes.stream().mapToDouble(TopologyNode::y).average().orElse(100);
                    if (operations.size() > 1 && operations.get(1).sourceNodeId() != null) {
                        TopologyNode source = requireNode(nodes, operations.get(1).sourceNodeId());
                        x = source.x() + 270;
                        y = source.y() + 100;
                    }
                    while (overlaps(nodes, x, y)) { x += 40; y += 90; }
                    nodes.add(new TopologyNode(op.nodeId(), op.componentType(), op.displayName(), x, y, op.config()));
                }
                case REMOVE_NODE -> {
                    requireNode(nodes, op.nodeId());
                    nodes.removeIf(node -> node.id().equals(op.nodeId()));
                    edges.removeIf(edge -> edge.source().equals(op.nodeId()) || edge.target().equals(op.nodeId()));
                }
                case CONNECT -> {
                    if (hasEdge(edges, op.sourceNodeId(), op.targetNodeId(), op.relationType())) throw conflict("该连接已经存在");
                    edges.add(new TopologyEdge(UUID.randomUUID(), op.sourceNodeId(), op.targetNodeId(), op.relationType()));
                }
                case DISCONNECT -> {
                    if (!edges.removeIf(edge -> edge.source().equals(op.sourceNodeId())
                            && edge.target().equals(op.targetNodeId()) && edge.relationType() == op.relationType()))
                        throw bad("当前架构中没有这条连接");
                }
                case UPDATE_CONFIG -> {
                    TopologyNode old = requireNode(nodes, op.nodeId());
                    Map<String, String> config = new LinkedHashMap<>(old.config());
                    config.put(op.configKey(), op.newValue());
                    nodes.replaceAll(node -> node.id().equals(op.nodeId())
                            ? new TopologyNode(node.id(), node.componentType(), node.displayName(), node.x(), node.y(), config) : node);
                }
            }
        }
        validator.validate(nodes, edges);
        return new Topology(current.projectId(), current.revision(), nodes, edges);
    }

    private static boolean overlaps(List<TopologyNode> nodes, double x, double y) {
        for (TopologyNode node : nodes) if (Math.abs(node.x() - x) < 210 && Math.abs(node.y() - y) < 120) return true;
        return false;
    }
    private static TopologyNode requireNode(Topology topology, UUID id) { return requireNode(topology.nodes(), id); }
    private static TopologyNode requireNode(List<TopologyNode> nodes, UUID id) {
        return nodes.stream().filter(node -> node.id().equals(id)).findFirst().orElseThrow(() -> bad("节点不在当前项目架构中"));
    }
    private static boolean hasEdge(Topology topology, UUID source, UUID target, RelationType relation) {
        return hasEdge(topology.edges(), source, target, relation);
    }
    private static boolean hasEdge(List<TopologyEdge> edges, UUID source, UUID target, RelationType relation) {
        return edges.stream().anyMatch(edge -> edge.source().equals(source) && edge.target().equals(target)
                && edge.relationType() == relation);
    }
    private static ApiException bad(String text) { return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROPOSAL", text); }
    private static ApiException conflict(String text) { return new ApiException(HttpStatus.CONFLICT, "PROPOSAL_CONFLICT", text); }
}
