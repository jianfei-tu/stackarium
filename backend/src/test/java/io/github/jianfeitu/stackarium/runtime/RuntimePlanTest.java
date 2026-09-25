package io.github.jianfeitu.stackarium.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jianfeitu.stackarium.common.ApiException;
import io.github.jianfeitu.stackarium.component.ComponentDefinition;
import io.github.jianfeitu.stackarium.component.MySqlPlugin;
import io.github.jianfeitu.stackarium.component.NginxPlugin;
import io.github.jianfeitu.stackarium.component.RabbitMqPlugin;
import io.github.jianfeitu.stackarium.component.RedisPlugin;
import io.github.jianfeitu.stackarium.plugin.PluginRegistry;
import io.github.jianfeitu.stackarium.topology.Topology;
import io.github.jianfeitu.stackarium.topology.TopologyNode;
import io.github.jianfeitu.stackarium.topology.TopologyValidator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimePlanTest {
    private final PluginRegistry plugins = new PluginRegistry(List.of(new MySqlPlugin(), new RedisPlugin(),
            new RabbitMqPlugin(), new NginxPlugin(), () -> new ComponentDefinition("spring-service", "Spring Boot",
                    "应用", "仅拓扑", List.of(new ComponentDefinition.ConfigField("serviceName", "服务名", "text", "app", true),
                            new ComponentDefinition.ConfigField("port", "端口", "number", "8080", true)),
                    List.of(), false)));
    private final RuntimePlanGenerator generator = new RuntimePlanGenerator(plugins, new TopologyValidator(plugins));

    @Test
    void fourPluginsProduceIndependentExecutableServices() {
        UUID project = UUID.randomUUID();
        RuntimePlan plan = generator.generate(new Topology(project, 7, List.of(
                node("mysql", Map.of("database", "orders", "port", "3306")),
                node("redis", Map.of("port", "6379")),
                node("rabbitmq", Map.of("queue", "events", "port", "5672")),
                node("nginx", Map.of("port", "80"))), List.of()));
        assertThat(plan.projectId()).isEqualTo(project);
        assertThat(plan.topologyRevision()).isEqualTo(7);
        assertThat(plan.services()).extracting(RuntimePlan.Service::componentType)
                .containsExactly("mysql", "redis", "rabbitmq", "nginx");
        assertThat(plan.services()).allSatisfy(service -> {
            assertThat(service.serviceName()).matches("n[a-f0-9]{32}");
            assertThat(service.healthcheck()).isNotEmpty();
            assertThat(service.image()).isNotBlank();
        });
        assertThat(plan.services().get(0).environment()).containsEntry("MYSQL_DATABASE", "orders");
        assertThat(plan.services().get(3).files()).containsKey("nginx.conf");
    }

    @Test
    void unsupportedAndInvalidConfigurationsFailBeforeCompose() {
        UUID project = UUID.randomUUID();
        assertThatThrownBy(() -> generator.generate(new Topology(project, 1,
                List.of(node("spring-service", Map.of("serviceName", "orders", "port", "8080"))), List.of())))
                .isInstanceOf(ApiException.class).hasMessageContaining("暂不支持运行");
        assertThatThrownBy(() -> generator.generate(new Topology(project, 1,
                List.of(node("mysql", Map.of("database", "bad-name", "port", "3306"))), List.of())))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> generator.generate(new Topology(project, 1, List.of(), List.of())))
                .isInstanceOf(ApiException.class).hasMessageContaining("添加");
    }

    private static TopologyNode node(String type, Map<String, String> config) {
        return new TopologyNode(UUID.randomUUID(), type, type, 0, 0, config);
    }
}
