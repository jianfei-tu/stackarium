package io.github.jianfeitu.stackarium.order;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import io.github.jianfeitu.stackarium.experiment.EventReporter;
import io.github.jianfeitu.stackarium.experiment.OrderCreated;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
class OrderService {
    record Created(String orderId, String sku, int remaining, String traceId) {}

    private final InventoryClient inventory;
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    private final EventReporter events;

    OrderService(InventoryClient inventory, RabbitTemplate rabbit, ObjectMapper json, EventReporter events) {
        this.inventory = inventory;
        this.rabbit = rabbit;
        this.json = json;
        this.events = events;
    }

    InventoryClient.Product product(String sku, String traceId) {
        long start = System.nanoTime();
        trace(traceId, "order.received", "RECEIVED");
        trace(traceId, "inventory.called", "CALLING");
        InventoryClient.Product result = inventory.get(sku, traceId);
        complete(traceId, "200", start);
        return result;
    }

    Created create(String sku, int quantity, String traceId) throws BlockException {
        long start = System.nanoTime();
        trace(traceId, "order.received", "RECEIVED");
        try (Entry ignored = SphU.entry("orders-create")) {
            trace(traceId, "inventory.called", "CALLING");
            InventoryClient.Product product = inventory.reserve(sku, quantity, traceId);
            String orderId = UUID.randomUUID().toString();
            String messageId = UUID.randomUUID().toString();
            OrderCreated message = new OrderCreated(messageId, orderId, sku, quantity, traceId);
            rabbit.convertAndSend("stackarium.orders", "order.created", json.writeValueAsString(message));
            events.publish("MESSAGE_PUBLISHED", Map.of("traceId", traceId, "messageId", messageId,
                    "routingKey", "order.created", "producer", "order-service"));
            complete(traceId, "201", start);
            return new Created(orderId, product.sku(), product.available(), traceId);
        } catch (BlockException blocked) {
            events.publish("SENTINEL_BLOCKED", Map.of("traceId", traceId, "resource", "orders-create",
                    "reason", "订单创建请求过于频繁"));
            trace(traceId, "order.blocked", "429");
            throw blocked;
        }
    }

    private void complete(String traceId, String status, long start) {
        events.publish("REQUEST_TRACE", Map.of("traceId", traceId, "stage", "order.completed",
                "service", "order-service", "status", status,
                "durationMs", Long.toString((System.nanoTime() - start) / 1_000_000)));
    }

    private void trace(String traceId, String stage, String status) {
        events.publish("REQUEST_TRACE", Map.of("traceId", traceId, "stage", stage,
                "service", "order-service", "status", status));
    }
}
