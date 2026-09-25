package io.github.jianfeitu.stackarium.component;

import io.github.jianfeitu.stackarium.common.ApiException;
import io.github.jianfeitu.stackarium.topology.TopologyNode;
import org.springframework.http.HttpStatus;

final class RuntimePluginChecks {
    private RuntimePluginChecks() {}

    static void fixedPort(TopologyNode node, int expected) {
        if (!Integer.toString(expected).equals(node.config().get("port")))
            throw new ApiException(HttpStatus.BAD_REQUEST, "RUNTIME_CONFIG",
                    node.displayName() + " 的运行端口目前必须为 " + expected);
    }

    static String database(TopologyNode node) {
        String name = node.config().get("database");
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_]{0,31}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "RUNTIME_CONFIG",
                    node.displayName() + " 的数据库名仅支持字母开头、字母数字和下划线，最多 32 位");
        return name;
    }
}
