package io.github.jianfeitu.stackarium.order;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "inventory-service")
interface InventoryClient {
    record Product(String sku, int available) {}

    @GetMapping("/internal/inventory/{sku}")
    Product get(@PathVariable String sku, @RequestHeader("X-Trace-Id") String traceId);

    @PostMapping("/internal/inventory/{sku}/reserve")
    Product reserve(@PathVariable String sku, @RequestParam int quantity,
                    @RequestHeader("X-Trace-Id") String traceId);
}
