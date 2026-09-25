package io.github.jianfeitu.stackarium.runtime;

import java.time.Instant;
import java.util.UUID;

public record ContainerObservation(UUID nodeId, String componentType, String displayName,
                                   String serviceName, String containerId, String state,
                                   String health, Instant observedAt) {}
