package io.github.jianfeitu.stackarium.inventory;

import io.github.jianfeitu.stackarium.experiment.EventReporter;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {
    public record Product(String sku, int available) {}

    private final JdbcTemplate database;
    private final StringRedisTemplate redis;
    private final EventReporter events;

    public InventoryService(JdbcTemplate database, StringRedisTemplate redis, EventReporter events) {
        this.database = database;
        this.redis = redis;
        this.events = events;
    }

    @PostConstruct
    void seed() {
        database.execute("CREATE TABLE IF NOT EXISTS products (sku VARCHAR(40) PRIMARY KEY, stock INT NOT NULL)");
        database.update("INSERT IGNORE INTO products (sku, stock) VALUES (?, ?)", "demo-sku", 50);
    }

    public Product get(String sku, String traceId) {
        String key = "product:" + sku;
        long start = System.nanoTime();
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            publish(traceId, "HIT", key, start);
            return new Product(sku, Integer.parseInt(cached));
        }
        publish(traceId, "MISS", key, start);
        Integer stock = database.queryForObject("SELECT stock FROM products WHERE sku = ?", Integer.class, sku);
        redis.opsForValue().set(key, Integer.toString(stock), Duration.ofMinutes(5));
        publish(traceId, "WRITE", key, start);
        return new Product(sku, stock);
    }

    public Product reserve(String sku, int quantity, String traceId) {
        int updated = database.update("UPDATE products SET stock = stock - ? WHERE sku = ? AND stock >= ?",
                quantity, sku, quantity);
        if (updated != 1) throw new IllegalArgumentException("库存不足或商品不存在");
        long start = System.nanoTime();
        redis.delete("product:" + sku);
        publish(traceId, "WRITE", "product:" + sku, start);
        return new Product(sku,
                database.queryForObject("SELECT stock FROM products WHERE sku = ?", Integer.class, sku));
    }

    private void publish(String traceId, String result, String key, long start) {
        events.publish("CACHE_ACCESS", Map.of("traceId", traceId, "service", "inventory-service",
                "cacheName", "products", "key", key, "result", result,
                "durationMs", Long.toString((System.nanoTime() - start) / 1_000_000)));
    }
}
