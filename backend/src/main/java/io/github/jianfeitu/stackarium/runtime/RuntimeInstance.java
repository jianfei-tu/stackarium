package io.github.jianfeitu.stackarium.runtime;

import java.time.Instant;
import java.util.UUID;

public record RuntimeInstance(UUID id, UUID projectId, long topologyRevision,
                              String composeProjectName, String artifactPath, RuntimePlan plan,
                              RuntimeStatus status, String errorPhase, UUID errorNodeId,
                              String errorMessage, Instant createdAt, Instant startedAt,
                              Instant stoppedAt, Instant updatedAt) {}
