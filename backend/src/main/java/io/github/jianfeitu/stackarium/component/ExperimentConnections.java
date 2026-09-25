package io.github.jianfeitu.stackarium.component;

import io.github.jianfeitu.stackarium.common.ApiException;
import io.github.jianfeitu.stackarium.plugin.RuntimeConnection;
import io.github.jianfeitu.stackarium.topology.RelationType;
import java.util.List;
import org.springframework.http.HttpStatus;

final class ExperimentConnections {
    private ExperimentConnections() {}

    static RuntimeConnection required(List<RuntimeConnection> connections, RelationType relation,
                                      String type, String role) {
        List<RuntimeConnection> matches = connections.stream()
                .filter(item -> item.relation() == relation && item.target().componentType().equals(type)
                        && (role == null || role.equals(item.target().config().get("serviceName"))))
                .toList();
        if (matches.size() != 1) throw new ApiException(HttpStatus.BAD_REQUEST, "EXPERIMENT_CONNECTION",
                "实验需要恰好一条 " + relation + " 连接到 " + (role == null ? type : role));
        return matches.get(0);
    }
}
