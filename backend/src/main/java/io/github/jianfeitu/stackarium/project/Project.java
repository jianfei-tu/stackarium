package io.github.jianfeitu.stackarium.project;

import java.time.Instant;
import java.util.UUID;

public record Project(UUID id, String name, String description, Instant createdAt, Instant updatedAt) {}
