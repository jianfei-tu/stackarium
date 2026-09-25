package io.github.jianfeitu.stackarium.assistant;

import io.github.jianfeitu.stackarium.common.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class AssistantRepository {
    public record Conversation(UUID id, UUID projectId, Instant createdAt, Instant updatedAt) {}
    public record Message(UUID id, String role, String content, Instant createdAt) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AssistantRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Conversation createConversation(UUID projectId) {
        Instant now = Instant.now();
        Conversation conversation = new Conversation(UUID.randomUUID(), projectId, now, now);
        jdbc.update("INSERT INTO assistant_conversations(id,project_id,created_at,updated_at) VALUES (?,?,?,?)",
                conversation.id().toString(), projectId.toString(), Timestamp.from(now), Timestamp.from(now));
        return conversation;
    }

    public Conversation conversation(UUID projectId, UUID id) {
        return jdbc.query("SELECT * FROM assistant_conversations WHERE id=? AND project_id=?",
                (rs, row) -> new Conversation(UUID.fromString(rs.getString("id")), projectId,
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
                id.toString(), projectId.toString()).stream().findFirst().orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "会话不存在或不属于当前项目"));
    }

    public List<Conversation> conversations(UUID projectId) {
        return jdbc.query("SELECT c.* FROM assistant_conversations c WHERE c.project_id=? "
                        + "AND (EXISTS (SELECT 1 FROM assistant_messages m WHERE m.conversation_id=c.id) "
                        + "OR EXISTS (SELECT 1 FROM assistant_proposals p WHERE p.conversation_id=c.id)) "
                        + "ORDER BY c.updated_at DESC LIMIT 30",
                (rs, row) -> new Conversation(UUID.fromString(rs.getString("id")), projectId,
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
                projectId.toString());
    }

    public void addMessage(UUID conversationId, String role, String content) {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO assistant_messages(id,conversation_id,role,content,created_at) VALUES (?,?,?,?,?)",
                UUID.randomUUID().toString(), conversationId.toString(), role, Secrets.redact(content), Timestamp.from(now));
        jdbc.update("UPDATE assistant_conversations SET updated_at=? WHERE id=?", Timestamp.from(now), conversationId.toString());
    }

    public List<Message> messages(UUID conversationId) {
        return jdbc.query("SELECT * FROM assistant_messages WHERE conversation_id=? ORDER BY created_at,id",
                (rs, row) -> new Message(UUID.fromString(rs.getString("id")), rs.getString("role"),
                        rs.getString("content"), rs.getTimestamp("created_at").toInstant()), conversationId.toString());
    }

    public List<Message> recentMessages(UUID conversationId) {
        return jdbc.query("SELECT * FROM (SELECT * FROM assistant_messages WHERE conversation_id=? ORDER BY created_at DESC,id DESC LIMIT 8) recent ORDER BY created_at,id",
                (rs, row) -> new Message(UUID.fromString(rs.getString("id")), rs.getString("role"),
                        rs.getString("content"), rs.getTimestamp("created_at").toInstant()), conversationId.toString());
    }

    public void addProposal(ChangeProposal proposal) {
        Timestamp now = Timestamp.from(proposal.createdAt());
        jdbc.update("INSERT INTO assistant_proposals(id,project_id,conversation_id,expected_revision,summary,operations_json,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                proposal.id().toString(), proposal.projectId().toString(), proposal.conversationId().toString(),
                proposal.expectedRevision(), proposal.summary(), json.writeValueAsString(proposal.operations()),
                proposal.status().name(), now, now);
    }

    public ChangeProposal proposal(UUID projectId, UUID id) {
        return jdbc.query("SELECT * FROM assistant_proposals WHERE id=? AND project_id=?",
                (rs, row) -> new ChangeProposal(UUID.fromString(rs.getString("id")), projectId,
                        UUID.fromString(rs.getString("conversation_id")), rs.getLong("expected_revision"),
                        rs.getString("summary"), json.readValue(rs.getString("operations_json"),
                                new TypeReference<List<ChangeOperation>>() {}),
                        ChangeProposal.Status.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant()), id.toString(), projectId.toString())
                .stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "PROPOSAL_NOT_FOUND", "建议修改不存在或不属于当前项目"));
    }

    public List<ChangeProposal> proposals(UUID projectId, UUID conversationId) {
        return jdbc.query("SELECT id FROM assistant_proposals WHERE project_id=? AND conversation_id=? ORDER BY created_at,id",
                (rs, row) -> proposal(projectId, UUID.fromString(rs.getString("id"))),
                projectId.toString(), conversationId.toString());
    }

    public boolean updateProposalStatus(UUID projectId, UUID id, ChangeProposal.Status from, ChangeProposal.Status to) {
        return jdbc.update("UPDATE assistant_proposals SET status=?,updated_at=? WHERE id=? AND project_id=? AND status=?",
                to.name(), Timestamp.from(Instant.now()), id.toString(), projectId.toString(), from.name()) == 1;
    }
}
