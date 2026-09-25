package io.github.jianfeitu.stackarium.topology;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/topology")
public class TopologyController {
    public record SaveTopologyRequest(long expectedRevision, List<TopologyNode> nodes, List<TopologyEdge> edges) {}

    private final TopologyService service;

    public TopologyController(TopologyService service) { this.service = service; }

    @GetMapping
    public Topology get(@PathVariable UUID projectId) { return service.get(projectId); }

    @PutMapping
    public Topology save(@PathVariable UUID projectId, @RequestBody SaveTopologyRequest request) {
        return service.save(projectId, request.expectedRevision(), request.nodes(), request.edges());
    }
}
