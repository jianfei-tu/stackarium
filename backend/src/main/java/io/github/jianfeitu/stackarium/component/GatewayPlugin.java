package io.github.jianfeitu.stackarium.component;

import io.github.jianfeitu.stackarium.common.ApiException;
import io.github.jianfeitu.stackarium.plugin.ComponentPlugin;
import io.github.jianfeitu.stackarium.plugin.RuntimeConnection;
import io.github.jianfeitu.stackarium.plugin.RuntimeContribution;
import io.github.jianfeitu.stackarium.topology.RelationType;
import io.github.jianfeitu.stackarium.topology.TopologyNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class GatewayPlugin implements ComponentPlugin {
    @Override
    public ComponentDefinition definition() {
        return new ComponentDefinition("gateway", "Gateway", "流量入口", "通过 Nacos 发现 order-service 的实验入口",
                List.of(new ComponentDefinition.ConfigField("routePrefix", "路由前缀", "text", "/api", true),
                        new ComponentDefinition.ConfigField("port", "端口", "number", "8080", true)),
                List.of(new ComponentDefinition.ConnectionRule(RelationType.PROXY, List.of("spring-service")),
                        new ComponentDefinition.ConnectionRule(RelationType.DISCOVERY, List.of("nacos"))), true);
    }

    @Override
    public Optional<RuntimeContribution> runtime(TopologyNode node, List<RuntimeConnection> connections) {
        RuntimePluginChecks.fixedPort(node, 8080);
        if (!"/api".equals(node.config().get("routePrefix")))
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXPERIMENT_ROUTE", "当前实验入口固定为 /api");
        ExperimentConnections.required(connections, RelationType.PROXY, "spring-service", "order-service");
        String nacos = ExperimentConnections.required(connections, RelationType.DISCOVERY,
                "nacos", null).serviceName();
        return Optional.of(new RuntimeContribution("stackarium/gateway-service:demo",
                Map.of("NACOS_SERVER", nacos + ":8848"), List.of(8080), Map.of(),
                List.of("CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:8080/actuator/health"),
                35, Map.of(), "gateway-service", 18080));
    }
}
