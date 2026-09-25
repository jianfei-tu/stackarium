package io.github.jianfeitu.stackarium.runtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RuntimeWebSocketConfig implements WebSocketConfigurer {
    private final RuntimeEventHub events;

    public RuntimeWebSocketConfig(RuntimeEventHub events) { this.events = events; }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(events, "/ws/projects/*/runtime")
                .setAllowedOrigins("http://127.0.0.1:5173", "http://localhost:5173");
    }
}
