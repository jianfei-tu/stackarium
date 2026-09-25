package io.github.jianfeitu.stackarium.assistant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChangeProposal(UUID id, UUID projectId, UUID conversationId, long expectedRevision,
                             String summary, List<ChangeOperation> operations, Status status, Instant createdAt) {
    public enum Status { PENDING, CONFIRMED, CANCELLED, STALE }
}
