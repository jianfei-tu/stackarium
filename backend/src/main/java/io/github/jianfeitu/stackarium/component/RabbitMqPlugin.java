package io.github.jianfeitu.stackarium.component;

import io.github.jianfeitu.stackarium.plugin.ComponentPlugin;
import io.github.jianfeitu.stackarium.plugin.RuntimeContribution;
import io.github.jianfeitu.stackarium.topology.TopologyNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqPlugin implements ComponentPlugin {
    @Override
    public ComponentDefinition definition() {
        return new ComponentDefinition("rabbitmq", "RabbitMQ", "消息", "订单服务发布 OrderCreated，通知消费者异步消费；由 MESSAGE 连接表达。",
                List.of(new ComponentDefinition.ConfigField("queue", "计划队列名", "text", "events", true),
                        new ComponentDefinition.ConfigField("port", "端口", "number", "5672", true)),
                List.of(), true);
    }

    @Override
    public Optional<RuntimeContribution> runtime(TopologyNode node) {
        RuntimePluginChecks.fixedPort(node, 5672);
        return Optional.of(new RuntimeContribution("rabbitmq:4.3.5-management", Map.of(), List.of(5672, 15672),
                Map.of("data", "/var/lib/rabbitmq"),
                List.of("CMD", "rabbitmq-diagnostics", "-q", "ping"), 30, Map.of()));
    }
}
