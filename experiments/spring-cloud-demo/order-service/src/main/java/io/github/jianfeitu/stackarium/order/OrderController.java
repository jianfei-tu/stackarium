package io.github.jianfeitu.stackarium.order;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    public record CreateOrder(String sku, int quantity) {}

    private final OrderService orders;

    public OrderController(OrderService orders) { this.orders = orders; }

    @GetMapping("/products/{sku}")
    public InventoryClient.Product product(@PathVariable String sku,
                                           @RequestHeader("X-Trace-Id") String traceId) {
        return orders.product(sku, traceId);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateOrder request,
                                    @RequestHeader("X-Trace-Id") String traceId) {
        if (request.sku() == null || !request.sku().matches("[a-z0-9-]{1,40}")
                || request.quantity() < 1 || request.quantity() > 10)
            return ResponseEntity.badRequest().body(Map.of("error", "商品或数量无效", "traceId", traceId));
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(orders.create(request.sku(), request.quantity(), traceId));
        } catch (BlockException blocked) {
            return ResponseEntity.status(429).body(Map.of("error", "请求过于频繁，已被 Sentinel 限流",
                    "traceId", traceId));
        }
    }
}
