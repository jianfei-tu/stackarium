package io.github.jianfeitu.stackarium.runtime;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/runtime")
public class RuntimeController {
    private final RuntimeService service;

    public RuntimeController(RuntimeService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<RuntimeView> latest(@PathVariable UUID projectId) {
        return service.latest(projectId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/generate")
    public RuntimeView generate(@PathVariable UUID projectId) { return service.generate(projectId); }

    @GetMapping("/{runtimeId}")
    public RuntimeView get(@PathVariable UUID projectId, @PathVariable UUID runtimeId) {
        return service.get(projectId, runtimeId);
    }

    @PostMapping("/{runtimeId}/start")
    public RuntimeView start(@PathVariable UUID projectId, @PathVariable UUID runtimeId) {
        return service.start(projectId, runtimeId);
    }

    @PostMapping("/{runtimeId}/stop")
    public RuntimeView stop(@PathVariable UUID projectId, @PathVariable UUID runtimeId) {
        return service.stop(projectId, runtimeId);
    }

    @GetMapping("/{runtimeId}/events")
    public List<RuntimeEvent> events(@PathVariable UUID projectId, @PathVariable UUID runtimeId) {
        return service.events(projectId, runtimeId);
    }

    @GetMapping("/{runtimeId}/logs")
    public RuntimeService.Logs logs(@PathVariable UUID projectId, @PathVariable UUID runtimeId,
                                    @RequestParam UUID nodeId,
                                    @RequestParam(defaultValue = "100") int lines) {
        return service.logs(projectId, runtimeId, nodeId, lines);
    }
}
