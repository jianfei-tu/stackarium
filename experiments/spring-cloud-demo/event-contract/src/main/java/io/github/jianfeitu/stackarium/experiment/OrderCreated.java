package io.github.jianfeitu.stackarium.experiment;

public record OrderCreated(String messageId, String orderId, String sku, int quantity, String traceId) {}
