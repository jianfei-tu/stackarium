package io.github.jianfeitu.stackarium.runtime;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RuntimeEvent(UUID eventId, UUID runtimeId, Instant timestamp, String eventType,
                           UUID nodeId, Map<String, String> payload) {}
