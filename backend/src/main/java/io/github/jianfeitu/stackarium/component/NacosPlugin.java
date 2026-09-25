package io.github.jianfeitu.stackarium.component;

import io.github.jianfeitu.stackarium.common.ApiException;
import io.github.jianfeitu.stackarium.plugin.ComponentPlugin;
import io.github.jianfeitu.stackarium.plugin.RuntimeContribution;
import io.github.jianfeitu.stackarium.topology.TopologyNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class NacosPlugin implements ComponentPlugin {
    @Override
    public ComponentDefinition definition() {
        return new ComponentDefinition("nacos", "Nacos", "治理", "实验服务注册与发现",
                List.of(new ComponentDefinition.ConfigField("namespace", "命名空间", "text", "public", true),
                        new ComponentDefinition.ConfigField("port", "端口", "number", "8848", true)),
                List.of(), true);
    }

    @Override
    public Optional<RuntimeContribution> runtime(TopologyNode node) {
        RuntimePluginChecks.fixedPort(node, 8848);
        if (!"public".equals(node.config().get("namespace")))
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXPERIMENT_NAMESPACE", "当前实验使用 public 命名空间");
        return Optional.of(new RuntimeContribution("nacos/nacos-server:v3.2.4",
                Map.of("MODE", "standalone", "NACOS_AUTH_ENABLE", "false",
                        "NACOS_AUTH_TOKEN", "${NACOS_AUTH_TOKEN}",
                        "NACOS_AUTH_IDENTITY_KEY", "stackarium",
                        "NACOS_AUTH_IDENTITY_VALUE", "${NACOS_AUTH_IDENTITY_VALUE}",
                        "JVM_XMS", "256m", "JVM_XMX", "512m", "JVM_XMN", "128m"),
                List.of(8848, 9848), Map.of("data", "/home/nacos/data"),
                List.of("CMD-SHELL", "curl -fsS http://127.0.0.1:8848/nacos/v3/admin/core/state/readiness >/dev/null"),
                80, Map.of()));
    }
}
