package io.github.jianfeitu.stackarium.topology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jianfeitu.stackarium.common.ApiException;
import io.github.jianfeitu.stackarium.project.Project;
import io.github.jianfeitu.stackarium.project.ProjectService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "STACKARIUM_DB_PASSWORD", matches = ".+")
class TopologyPersistenceIntegrationTest {
    @Autowired ProjectService projects;
    @Autowired TopologyService topologies;
    @Autowired JdbcTemplate jdbc;
    private UUID projectId;

    @AfterEach
    void cleanUp() {
        if (projectId != null) jdbc.update("DELETE FROM projects WHERE id = ?", projectId.toString());
    }

    @Test
    void savesReloadsAndRejectsStaleRevisionWithoutLosingData() {
        Project project = projects.create("集成验证", "真实 MySQL");
        projectId = project.id();
        TopologyNode service = new TopologyNode(UUID.randomUUID(), "spring-service", "订单服务", 123.5, 260,
                Map.of("serviceName", "order-service", "port", "8080"));
        TopologyNode database = new TopologyNode(UUID.randomUUID(), "mysql", "订单库", 420, 260,
                Map.of("database", "orders", "port", "3306"));
        TopologyEdge edge = new TopologyEdge(UUID.randomUUID(), service.id(), database.id(), RelationType.CALL);

        Topology saved = topologies.save(projectId, 0, List.of(service, database), List.of(edge));
        Topology loaded = topologies.get(projectId);
        assertThat(saved.revision()).isEqualTo(1);
        assertThat(loaded.nodes()).containsExactlyInAnyOrder(service, database);
        assertThat(loaded.edges()).containsExactly(edge);
        assertThatThrownBy(() -> topologies.save(projectId, 0, List.of(), List.of()))
                .isInstanceOf(ApiException.class).hasMessageContaining("刷新");
        assertThat(topologies.get(projectId).nodes()).hasSize(2);
    }
}
