package io.github.jianfeitu.stackarium.plugin;

import java.util.List;
import java.util.Map;

public record RuntimeContribution(String image, Map<String, String> environment, List<Integer> ports,
                                  Map<String, String> volumes, List<String> healthcheck,
                                  int startPeriodSeconds, Map<String, String> files,
                                  String buildModule, Integer publishedPort) {
    public RuntimeContribution(String image, Map<String, String> environment, List<Integer> ports,
                               Map<String, String> volumes, List<String> healthcheck,
                               int startPeriodSeconds, Map<String, String> files) {
        this(image, environment, ports, volumes, healthcheck, startPeriodSeconds, files, null, null);
    }
}
