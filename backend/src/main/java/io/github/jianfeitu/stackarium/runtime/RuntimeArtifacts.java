package io.github.jianfeitu.stackarium.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RuntimeArtifacts {
    private final Path root;

    public RuntimeArtifacts(@Value("${stackarium.runtime.root}") String rootPath) {
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
    }

    public Path create(UUID projectId, UUID runtimeId) throws IOException {
        Files.createDirectories(root);
        Path workspace = root.resolve(projectId.toString()).resolve(runtimeId.toString()).normalize();
        if (!workspace.startsWith(root)) throw new IllegalArgumentException("Runtime path escapes generated root");
        Files.createDirectories(workspace);
        Path canonical = workspace.toRealPath();
        if (!canonical.startsWith(root.toRealPath()))
            throw new IllegalArgumentException("Runtime path escapes generated root");
        return canonical;
    }

    public Path verified(RuntimeInstance instance) throws IOException {
        Path canonicalRoot = root.toRealPath();
        Path expected = canonicalRoot.resolve(instance.projectId().toString()).resolve(instance.id().toString());
        Path actual = Path.of(instance.artifactPath()).toRealPath();
        if (!actual.equals(expected) || !actual.startsWith(canonicalRoot))
            throw new IllegalArgumentException("Runtime workspace is outside generated root");
        return actual;
    }
}
