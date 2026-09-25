package io.github.jianfeitu.stackarium.component;

import io.github.jianfeitu.stackarium.plugin.PluginRegistry;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/components")
public class ComponentController {
    private final PluginRegistry registry;

    public ComponentController(PluginRegistry registry) { this.registry = registry; }

    @GetMapping
    public List<ComponentDefinition> list() { return registry.list(); }
}
