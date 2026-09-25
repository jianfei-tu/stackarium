package io.github.jianfeitu.stackarium.runtime;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class RuntimeRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RowMapper<RuntimeInstance> mapper;

    public RuntimeRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
        this.mapper = (rs, row) -> new RuntimeInstance(
            UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("project_id")),
            rs.getLong("topology_revision"), rs.getString("compose_project_name"),
            rs.getString("artifact_path"), json.readValue(rs.getString("plan_json"), RuntimePlan.class),
            RuntimeStatus.valueOf(rs.getString("status")), rs.getString("error_phase"),
            rs.getString("error_node_id") == null ? null : UUID.fromString(rs.getString("error_node_id")),
            rs.getString("error_message"), rs.getTimestamp("created_at").toInstant(),
            instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("stopped_at")),
            rs.getTimestamp("updated_at").toInstant());
    }

    public void insert(RuntimeInstance instance) {
        jdbc.update("INSERT INTO runtime_instances(id, project_id, topology_revision, compose_project_name, artifact_path, plan_json, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                instance.id().toString(), instance.projectId().toString(), instance.topologyRevision(),
                instance.composeProjectName(), instance.artifactPath(), json.writeValueAsString(instance.plan()),
                instance.status().name(), Timestamp.from(instance.createdAt()), Timestamp.from(instance.updatedAt()));
    }

    public Optional<RuntimeInstance> find(UUID projectId, UUID runtimeId) {
        return jdbc.query("SELECT * FROM runtime_instances WHERE project_id = ? AND id = ?", mapper,
                projectId.toString(), runtimeId.toString()).stream().findFirst();
    }

    public Optional<RuntimeInstance> latest(UUID projectId) {
        return jdbc.query("SELECT * FROM runtime_instances WHERE project_id = ? ORDER BY created_at DESC, id DESC LIMIT 1",
                mapper, projectId.toString()).stream().findFirst();
    }

    public List<RuntimeInstance> watchable() {
        return jdbc.query("SELECT * FROM runtime_instances WHERE status IN ('PREPARING','STARTING','RUNNING','DEGRADED','STOPPING')",
                mapper);
    }

    public void status(UUID runtimeId, RuntimeStatus status, String phase, UUID nodeId, String message) {
        Instant now = Instant.now();
        jdbc.update("UPDATE runtime_instances SET status = ?, error_phase = ?, error_node_id = ?, error_message = ?, updated_at = ?, started_at = CASE WHEN ? = 'STARTING' AND started_at IS NULL THEN ? ELSE started_at END, stopped_at = CASE WHEN ? = 'STOPPED' THEN ? ELSE stopped_at END WHERE id = ?",
                status.name(), phase, nodeId == null ? null : nodeId.toString(), message, Timestamp.from(now),
                status.name(), Timestamp.from(now), status.name(), Timestamp.from(now), runtimeId.toString());
    }

    public void event(RuntimeEvent event) {
        jdbc.update("INSERT INTO runtime_events(id, runtime_id, event_type, node_id, payload_json, occurred_at) VALUES (?, ?, ?, ?, ?, ?)",
                event.eventId().toString(), event.runtimeId().toString(), event.eventType(),
                event.nodeId() == null ? null : event.nodeId().toString(), json.writeValueAsString(event.payload()),
                Timestamp.from(event.timestamp()));
    }

    public List<RuntimeEvent> events(UUID runtimeId) {
        return jdbc.query("SELECT * FROM runtime_events WHERE runtime_id = ? ORDER BY occurred_at DESC, id DESC LIMIT 100",
                (rs, row) -> new RuntimeEvent(UUID.fromString(rs.getString("id")), runtimeId,
                        rs.getTimestamp("occurred_at").toInstant(), rs.getString("event_type"),
                        rs.getString("node_id") == null ? null : UUID.fromString(rs.getString("node_id")),
                        json.readValue(rs.getString("payload_json"), new TypeReference<Map<String, String>>() {})),
                runtimeId.toString());
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
