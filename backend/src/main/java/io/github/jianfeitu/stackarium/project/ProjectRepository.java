package io.github.jianfeitu.stackarium.project;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<Project> mapper = (rs, rowNum) -> new Project(
            UUID.fromString(rs.getString("id")), rs.getString("name"), rs.getString("description"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());

    public ProjectRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(Project project) {
        jdbc.update("INSERT INTO projects(id, name, description, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                project.id().toString(), project.name(), project.description(),
                Timestamp.from(project.createdAt()), Timestamp.from(project.updatedAt()));
        jdbc.update("INSERT INTO topologies(project_id, revision, created_at, updated_at) VALUES (?, 0, ?, ?)",
                project.id().toString(), Timestamp.from(project.createdAt()), Timestamp.from(project.updatedAt()));
    }

    public Optional<Project> find(UUID id) {
        return jdbc.query("SELECT * FROM projects WHERE id = ?", mapper, id.toString()).stream().findFirst();
    }

    public List<Project> list() {
        return jdbc.query("SELECT * FROM projects ORDER BY updated_at DESC", mapper);
    }
}
