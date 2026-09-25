package io.github.jianfeitu.stackarium.component;

import io.github.jianfeitu.stackarium.plugin.ComponentPlugin;
import io.github.jianfeitu.stackarium.plugin.RuntimeContribution;
import io.github.jianfeitu.stackarium.topology.TopologyNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MySqlPlugin implements ComponentPlugin {
    @Override
    public ComponentDefinition definition() {
        return new ComponentDefinition("mysql", "MySQL", "数据存储", "关系型数据库",
                List.of(new ComponentDefinition.ConfigField("database", "数据库名", "text", "app", true),
                        new ComponentDefinition.ConfigField("port", "端口", "number", "3306", true)),
                List.of(), true);
    }

    @Override
    public Optional<RuntimeContribution> runtime(TopologyNode node) {
        RuntimePluginChecks.fixedPort(node, 3306);
        return Optional.of(new RuntimeContribution("mysql:8.4",
                Map.of("MYSQL_DATABASE", RuntimePluginChecks.database(node),
                        "MYSQL_ROOT_PASSWORD", "${MYSQL_ROOT_PASSWORD}"),
                List.of(3306), Map.of("data", "/var/lib/mysql"),
                List.of("CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -u root -p\"$$MYSQL_ROOT_PASSWORD\" --silent"),
                40, Map.of()));
    }
}
