package io.github.jianfeitu.stackarium.topology;

import io.github.jianfeitu.stackarium.common.ApiException;
import io.github.jianfeitu.stackarium.project.ProjectService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopologyService {
    private final ProjectService projects;
    private final TopologyRepository repository;
    private final TopologyValidator validator;

    public TopologyService(ProjectService projects, TopologyRepository repository, TopologyValidator validator) {
        this.projects = projects;
        this.repository = repository;
        this.validator = validator;
    }

    public Topology get(UUID projectId) {
        projects.get(projectId);
        return repository.load(projectId);
    }

    @Transactional
    public Topology save(UUID projectId, long expectedRevision, List<TopologyNode> nodes, List<TopologyEdge> edges) {
        projects.get(projectId);
        validator.validate(nodes, edges);
        if (!repository.replace(projectId, expectedRevision, nodes, edges))
            throw new ApiException(HttpStatus.CONFLICT, "REVISION_CONFLICT", "架构已在其他位置更新，请刷新后重试");
        return repository.load(projectId);
    }
}
