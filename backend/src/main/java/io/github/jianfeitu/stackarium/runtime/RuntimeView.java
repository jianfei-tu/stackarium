package io.github.jianfeitu.stackarium.runtime;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RuntimeView(UUID id, UUID projectId, long topologyRevision, RuntimeStatus status,
                          String errorPhase, UUID errorNodeId, String errorMessage,
                          Instant createdAt, Instant startedAt, Instant stoppedAt, Instant observedAt,
                          List<ContainerObservation> components) {}
