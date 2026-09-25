package io.github.jianfeitu.stackarium.component;

import io.github.jianfeitu.stackarium.common.ApiException;
import io.github.jianfeitu.stackarium.plugin.ComponentPlugin;
import io.github.jianfeitu.stackarium.plugin.RuntimeConnection;
import io.github.jianfeitu.stackarium.plugin.RuntimeContribution;
import io.github.jianfeitu.stackarium.topology.RelationType;
import io.github.jianfeitu.stackarium.topology.TopologyNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SpringServicePlugin implements ComponentPlugin {
    @Override
    public ComponentDefinition definition() {
        return new ComponentDefinition("spring-service", "Spring Boot Service", "应用服务",
                "实验角色：order-service 经 OpenFeign 调用库存并向 RabbitMQ 发布订单消息；inventory-service 使用 MySQL 和 Redis cache-aside；notification-consumer 消费消息。服务通过 Nacos 发现。",
                List.of(new ComponentDefinition.ConfigField("serviceName", "服务名", "text", "order-service", true),
                        new ComponentDefinition.ConfigField("port", "端口", "number", "8080", true)),
                List.of(new ComponentDefinition.ConnectionRule(RelationType.CALL, List.of("spring-service", "mysql")),
                        new ComponentDefinition.ConnectionRule(RelationType.CACHE, List.of("redis")),
                        new ComponentDefinition.ConnectionRule(RelationType.MESSAGE, List.of("rabbitmq")),
                        new ComponentDefinition.ConnectionRule(RelationType.DISCOVERY, List.of("nacos"))), true);
    }

    @Override
    public Optional<RuntimeContribution> runtime(TopologyNode node, List<RuntimeConnection> connections) {
        RuntimePluginChecks.fixedPort(node, 8080);
        String role = node.config().get("serviceName");
        if (!List.of("order-service", "inventory-service", "notification-consumer").contains(role))
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXPERIMENT_ROLE",
                    "当前只支持 order-service、inventory-service、notification-consumer 三种实验服务");
        Map<String, String> environment = new HashMap<>();
        environment.put("NACOS_SERVER", ExperimentConnections.required(connections, RelationType.DISCOVERY,
                "nacos", null).serviceName() + ":8848");
        switch (role) {
            case "order-service" -> {
                ExperimentConnections.required(connections, RelationType.CALL, "spring-service", "inventory-service");
                environment.put("RABBITMQ_HOST", ExperimentConnections.required(connections,
                        RelationType.MESSAGE, "rabbitmq", null).serviceName());
            }
            case "inventory-service" -> {
                RuntimeConnection mysql = ExperimentConnections.required(connections,
                        RelationType.CALL, "mysql", null);
                environment.put("MYSQL_HOST", mysql.serviceName());
                environment.put("MYSQL_DATABASE", mysql.target().config().get("database"));
                environment.put("MYSQL_ROOT_PASSWORD", "${MYSQL_ROOT_PASSWORD}");
                environment.put("REDIS_HOST", ExperimentConnections.required(connections,
                        RelationType.CACHE, "redis", null).serviceName());
            }
            case "notification-consumer" -> environment.put("RABBITMQ_HOST", ExperimentConnections.required(
                    connections, RelationType.MESSAGE, "rabbitmq", null).serviceName());
            default -> throw new IllegalStateException("Unexpected role");
        }
        return Optional.of(new RuntimeContribution("stackarium/" + role + ":demo", environment,
                List.of(8080), Map.of(), List.of("CMD-SHELL",
                        "wget -q -O /dev/null http://127.0.0.1:8080/actuator/health"),
                35, Map.of(), role, null));
    }
}
