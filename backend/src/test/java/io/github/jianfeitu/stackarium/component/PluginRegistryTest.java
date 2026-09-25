package io.github.jianfeitu.stackarium.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jianfeitu.stackarium.plugin.PluginRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class PluginRegistryTest {
    @Test
    void registersSevenComponentDefinitions() {
        PluginRegistry registry = new PluginRegistry(List.of(new SpringServicePlugin(), new MySqlPlugin(),
                new RedisPlugin(), new RabbitMqPlugin(), new NginxPlugin(), new NacosPlugin(),
                new GatewayPlugin()));

        assertThat(registry.list()).hasSize(7);
        assertThat(registry.get("spring-service").runtimeAvailable()).isTrue();
        assertThat(registry.get("spring-service").connections()).isNotEmpty();
    }

    @Test
    void rejectsDuplicateType() {
        assertThatThrownBy(() -> new PluginRegistry(List.of(new MySqlPlugin(), new MySqlPlugin())))
                .isInstanceOf(IllegalStateException.class);
    }
}
