package io.github.jianfeitu.stackarium.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jianfeitu.stackarium.runtime.DockerCommandExecutor.Operation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeInfrastructureTest {
    @TempDir Path temp;

    @Test
    void composeArtifactAndCommandStayInsideGeneratedWorkspace() throws Exception {
        UUID project = UUID.randomUUID();
        UUID runtime = UUID.randomUUID();
        UUID node = UUID.randomUUID();
        RuntimePlan.Service service = new RuntimePlan.Service(node, "nginx", "Nginx", "n" + node.toString().replace("-", ""),
                "nginx:1.27-alpine", Map.of(), List.of(80),
                Map.of("file:nginx.conf", "/etc/nginx/conf.d/default.conf:ro"), List.of(),
                List.of("CMD", "true"), 5, Map.of("nginx.conf", "server { listen 80; }"));
        RuntimePlan plan = new RuntimePlan(project, 1, List.of("stackarium"), List.of(service));
        RuntimeArtifacts artifacts = new RuntimeArtifacts(temp.toString());
        Path workspace = new ComposeGenerator(artifacts).generate(plan, runtime);
        String projectName = "stackarium-" + runtime.toString().replace("-", "").substring(0, 12);
        RuntimeInstance instance = instance(runtime, project, projectName, workspace, plan);
        assertThat(artifacts.verified(instance)).isEqualTo(workspace);
        assertThat(Files.readString(workspace.resolve("docker-compose.yml")))
                .contains("nginx:1.27-alpine", service.serviceName(), "healthcheck:");
        assertThat(Files.readString(workspace.resolve(service.serviceName() + "-nginx.conf")))
                .contains("listen 80");
        assertThat(DockerCommandExecutor.command(instance, Operation.UP, null, 0, projectName))
                .containsExactly("docker", "compose", "--project-name", projectName,
                        "--file", "docker-compose.yml", "up", "--build", "--detach");
        assertThatThrownBy(() -> DockerCommandExecutor.command(instance, Operation.LOGS, "other", 100, projectName))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> artifacts.verified(instance(runtime, project, projectName, temp, plan)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void healthAndFailureStatesRemainDistinct() {
        UUID node = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID runtime = UUID.randomUUID();
        RuntimePlan plan = new RuntimePlan(project, 1, List.of("stackarium"), List.of());
        RuntimeInstance starting = instance(runtime, project, "stackarium-test", temp, plan);
        assertThat(RuntimeService.derive(starting, List.of(observation(node, "running", "starting"))))
                .isEqualTo(RuntimeStatus.STARTING);
        assertThat(RuntimeService.derive(starting, List.of(observation(node, "running", "healthy"))))
                .isEqualTo(RuntimeStatus.RUNNING);
        assertThat(RuntimeService.derive(starting, List.of(observation(node, "running", "unhealthy"))))
                .isEqualTo(RuntimeStatus.DEGRADED);
        assertThat(RuntimeService.derive(starting, List.of(observation(node, "exited", "unhealthy"))))
                .isEqualTo(RuntimeStatus.FAILED);
        assertThat(RuntimeService.derive(starting, List.of(observation(node, "missing", "none"))))
                .isEqualTo(RuntimeStatus.STARTING);
    }

    @Test
    void dockerFailureDoesNotExposeGeneratedCredentials() {
        var failure = new DockerCommandExecutor.Result(1, "",
                "STACKARIUM_EVENT_TOKEN=event-value NACOS_AUTH_TOKEN: nacos-value "
                + "MYSQL_ROOT_PASSWORD=mysql-value Bearer bearer-value");
        assertThat(failure.errorSummary()).contains("STACKARIUM_EVENT_TOKEN=[隐藏]",
                "NACOS_AUTH_TOKEN=[隐藏]", "MYSQL_ROOT_PASSWORD=[隐藏]", "Bearer [隐藏]")
                .doesNotContain("event-value", "nacos-value", "mysql-value", "bearer-value");
    }

    private static RuntimeInstance instance(UUID runtime, UUID project, String name, Path path, RuntimePlan plan) {
        return new RuntimeInstance(runtime, project, 1, name, path.toString(), plan, RuntimeStatus.STARTING,
                null, null, null, Instant.now(), null, null, Instant.now());
    }

    private static ContainerObservation observation(UUID node, String state, String health) {
        return new ContainerObservation(node, "nginx", "Nginx", "service", "container", state, health, Instant.now());
    }
}
