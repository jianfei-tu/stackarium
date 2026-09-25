package io.github.jianfeitu.stackarium.project;

import io.github.jianfeitu.stackarium.common.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
    private final ProjectRepository projects;

    public ProjectService(ProjectRepository projects) { this.projects = projects; }

    @Transactional
    public Project create(String name, String description) {
        Instant now = Instant.now();
        Project project = new Project(UUID.randomUUID(), name.trim(), description == null ? "" : description.trim(), now, now);
        projects.insert(project);
        return project;
    }

    public Project get(UUID id) {
        return projects.find(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在"));
    }

    public List<Project> list() { return projects.list(); }
}
