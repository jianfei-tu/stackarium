package io.github.jianfeitu.stackarium.component;

import io.github.jianfeitu.stackarium.plugin.ComponentPlugin;
import io.github.jianfeitu.stackarium.plugin.RuntimeContribution;
import io.github.jianfeitu.stackarium.topology.TopologyNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class NginxPlugin implements ComponentPlugin {
    @Override
    public ComponentDefinition definition() {
        return new ComponentDefinition("nginx", "Nginx", "流量入口", "独立 HTTP 入口；后端代理留待服务实验",
                List.of(new ComponentDefinition.ConfigField("port", "端口", "number", "80", true)),
                List.of(new ComponentDefinition.ConnectionRule(io.github.jianfeitu.stackarium.topology.RelationType.PROXY,
                        List.of("gateway", "spring-service"))), true);
    }

    @Override
    public Optional<RuntimeContribution> runtime(TopologyNode node) {
        int port = Integer.parseInt(node.config().get("port"));
        String config = "server {\n    listen " + port + ";\n    server_name _;\n"
                + "    location /health { default_type text/plain; return 200 'ok'; }\n"
                + "    location / { default_type text/plain; return 200 'Stackarium runtime'; }\n}\n";
        return Optional.of(new RuntimeContribution("nginx:1.27-alpine", Map.of(), List.of(port),
                Map.of("file:nginx.conf", "/etc/nginx/conf.d/default.conf:ro"),
                List.of("CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:" + port + "/health"),
                5, Map.of("nginx.conf", config)));
    }
}
