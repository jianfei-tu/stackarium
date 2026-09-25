package io.github.jianfeitu.stackarium.gateway;

import io.github.jianfeitu.stackarium.experiment.EventReporter;
import java.util.Map;
import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TraceFilter implements GlobalFilter, Ordered {
    private final EventReporter events;

    public TraceFilter(EventReporter events) { this.events = events; }

    @Override public int getOrder() { return -100; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        String traceId = incoming != null && incoming.matches("[a-f0-9]{32}")
                ? incoming : UUID.randomUUID().toString().replace("-", "");
        long start = System.nanoTime();
        events.publish("REQUEST_TRACE", Map.of("traceId", traceId, "stage", "gateway.received",
                "service", "gateway-service", "status", "RECEIVED"));
        exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);
        ServerWebExchange traced = exchange.mutate().request(exchange.getRequest().mutate()
                .header("X-Trace-Id", traceId).build()).build();
        return chain.filter(traced).doFinally(signal -> {
            int code = exchange.getResponse().getStatusCode() == null ? 500
                    : exchange.getResponse().getStatusCode().value();
            events.publish("REQUEST_TRACE", Map.of("traceId", traceId, "stage", "gateway.routed",
                    "service", "gateway-service", "status", Integer.toString(code),
                    "durationMs", Long.toString((System.nanoTime() - start) / 1_000_000)));
        });
    }
}
