package io.github.jianfeitu.stackarium.runtime;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RuntimePlan(UUID projectId, long topologyRevision, List<String> networks, List<Service> services) {
    public record Service(UUID nodeId, String componentType, String displayName, String serviceName,
                          String image, Map<String, String> environment, List<Integer> ports,
                          Map<String, String> volumes, List<String> dependencies,
                          List<String> healthcheck, int startPeriodSeconds,
                          Map<String, String> files, String buildModule, Integer publishedPort) {
        public Service(UUID nodeId, String componentType, String displayName, String serviceName,
                       String image, Map<String, String> environment, List<Integer> ports,
                       Map<String, String> volumes, List<String> dependencies,
                       List<String> healthcheck, int startPeriodSeconds,
                       Map<String, String> files) {
            this(nodeId, componentType, displayName, serviceName, image, environment, ports,
                    volumes, dependencies, healthcheck, startPeriodSeconds, files, null, null);
        }
    }
}
