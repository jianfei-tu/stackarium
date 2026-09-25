package io.github.jianfeitu.stackarium.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    public record CreateProjectRequest(@NotBlank(message = "请输入项目名称") @Size(max = 120) String name,
                                       @Size(max = 500) String description) {}

    private final ProjectService service;

    public ProjectController(ProjectService service) { this.service = service; }

    @GetMapping
    public List<Project> list() { return service.list(); }

    @GetMapping("/{id}")
    public Project get(@PathVariable UUID id) { return service.get(id); }

    @PostMapping
    public ResponseEntity<Project> create(@Valid @RequestBody CreateProjectRequest request) {
        Project project = service.create(request.name(), request.description());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + project.id())).body(project);
    }
}
