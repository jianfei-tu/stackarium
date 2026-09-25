package io.github.jianfeitu.stackarium.inventory;

import io.github.jianfeitu.stackarium.experiment.EventReporter;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/inventory")
public class InventoryController {
    private final InventoryService inventory;
    private final EventReporter events;

    public InventoryController(InventoryService inventory, EventReporter events) {
        this.inventory = inventory;
        this.events = events;
    }

    @GetMapping("/{sku}")
    public InventoryService.Product get(@PathVariable String sku, @RequestHeader("X-Trace-Id") String traceId) {
        return run(sku, traceId, null);
    }

    @PostMapping("/{sku}/reserve")
    public InventoryService.Product reserve(@PathVariable String sku, @RequestParam int quantity,
                                            @RequestHeader("X-Trace-Id") String traceId) {
        return run(sku, traceId, quantity);
    }

    private InventoryService.Product run(String sku, String traceId, Integer quantity) {
        long start = System.nanoTime();
        events.publish("REQUEST_TRACE", Map.of("traceId", traceId, "stage", "inventory.received",
                "service", "inventory-service", "status", "RECEIVED"));
        InventoryService.Product result = quantity == null ? inventory.get(sku, traceId)
                : inventory.reserve(sku, quantity, traceId);
        events.publish("REQUEST_TRACE", Map.of("traceId", traceId, "stage", "inventory.completed",
                "service", "inventory-service", "status", "200",
                "durationMs", Long.toString((System.nanoTime() - start) / 1_000_000)));
        return result;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", error.getMessage()));
    }
}
