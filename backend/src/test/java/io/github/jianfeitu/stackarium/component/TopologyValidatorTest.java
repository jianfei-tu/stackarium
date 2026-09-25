package io.github.jianfeitu.stackarium.component;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jianfeitu.stackarium.common.ApiException;
import io.github.jianfeitu.stackarium.plugin.PluginRegistry;
import io.github.jianfeitu.stackarium.topology.RelationType;
import io.github.jianfeitu.stackarium.topology.TopologyEdge;
import io.github.jianfeitu.stackarium.topology.TopologyNode;
import io.github.jianfeitu.stackarium.topology.TopologyValidator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TopologyValidatorTest {
    private final TopologyValidator validator = new TopologyValidator(new PluginRegistry(List.of(
            new SpringServicePlugin(), new MySqlPlugin(), new RedisPlugin())));

    @Test
    void permitsServiceToMySqlCall() {
        TopologyNode service = service();
        TopologyNode mysql = mysql();
        validator.validate(List.of(service, mysql), List.of(new TopologyEdge(UUID.randomUUID(),
                service.id(), mysql.id(), RelationType.CALL)));
    }

    @Test
    void rejectsWrongRelationAndDanglingEndpoint() {
        TopologyNode service = service();
        TopologyNode mysql = mysql();
        assertThatThrownBy(() -> validator.validate(List.of(service, mysql), List.of(
                new TopologyEdge(UUID.randomUUID(), service.id(), mysql.id(), RelationType.CACHE))))
                .isInstanceOf(ApiException.class).hasMessageContaining("不支持");
        assertThatThrownBy(() -> validator.validate(List.of(service), List.of(
                new TopologyEdge(UUID.randomUUID(), service.id(), mysql.id(), RelationType.CALL))))
                .isInstanceOf(ApiException.class).hasMessageContaining("当前拓扑");
    }

    @Test
    void rejectsUnknownConfigAndInvalidPort() {
        TopologyNode service = service();
        assertThatThrownBy(() -> validator.validate(List.of(new TopologyNode(service.id(), service.componentType(),
                service.displayName(), 0, 0, Map.of("serviceName", "api", "port", "70000"))), List.of()))
                .isInstanceOf(ApiException.class).hasMessageContaining("65535");
        assertThatThrownBy(() -> validator.validate(List.of(new TopologyNode(service.id(), service.componentType(),
                service.displayName(), 0, 0, Map.of("serviceName", "api", "port", "8080", "password", "x"))), List.of()))
                .isInstanceOf(ApiException.class).hasMessageContaining("未定义字段");
    }

    private TopologyNode service() {
        return new TopologyNode(UUID.randomUUID(), "spring-service", "服务", 0, 0,
                Map.of("serviceName", "api", "port", "8080"));
    }

    private TopologyNode mysql() {
        return new TopologyNode(UUID.randomUUID(), "mysql", "数据库", 300, 0,
                Map.of("database", "app", "port", "3306"));
    }
}
