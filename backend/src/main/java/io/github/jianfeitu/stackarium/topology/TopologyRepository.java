package io.github.jianfeitu.stackarium.topology;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class TopologyRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public TopologyRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Topology load(UUID projectId) {
        Long revision = jdbc.queryForObject("SELECT revision FROM topologies WHERE project_id = ?", Long.class, projectId.toString());
        List<TopologyNode> nodes = jdbc.query("SELECT * FROM topology_nodes WHERE project_id = ? ORDER BY created_at, id",
                (rs, row) -> new TopologyNode(UUID.fromString(rs.getString("id")), rs.getString("component_type"),
                        rs.getString("display_name"), rs.getDouble("position_x"), rs.getDouble("position_y"),
                        json.readValue(rs.getString("config_json"), new TypeReference<Map<String, String>>() {})), projectId.toString());
        List<TopologyEdge> edges = jdbc.query("SELECT * FROM topology_edges WHERE project_id = ? ORDER BY created_at, id",
                (rs, row) -> new TopologyEdge(UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("source_node_id")), UUID.fromString(rs.getString("target_node_id")),
                        RelationType.valueOf(rs.getString("relation_type"))), projectId.toString());
        return new Topology(projectId, revision, nodes, edges);
    }

    public boolean replace(UUID projectId, long expectedRevision, List<TopologyNode> nodes, List<TopologyEdge> edges) {
        Timestamp now = Timestamp.from(Instant.now());
        int updated = jdbc.update("UPDATE topologies SET revision = revision + 1, updated_at = ? WHERE project_id = ? AND revision = ?",
                now, projectId.toString(), expectedRevision);
        if (updated == 0) return false;
        jdbc.update("DELETE FROM topology_edges WHERE project_id = ?", projectId.toString());
        jdbc.update("DELETE FROM topology_nodes WHERE project_id = ?", projectId.toString());
        for (TopologyNode node : nodes) {
            jdbc.update("INSERT INTO topology_nodes(id, project_id, component_type, display_name, position_x, position_y, config_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    node.id().toString(), projectId.toString(), node.componentType(), node.displayName(),
                    node.x(), node.y(), json.writeValueAsString(node.config()), now, now);
        }
        for (TopologyEdge edge : edges) {
            jdbc.update("INSERT INTO topology_edges(id, project_id, source_node_id, target_node_id, relation_type, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    edge.id().toString(), projectId.toString(), edge.source().toString(), edge.target().toString(),
                    edge.relationType().name(), now);
        }
        jdbc.update("UPDATE projects SET updated_at = ? WHERE id = ?", now, projectId.toString());
        return true;
    }
}
