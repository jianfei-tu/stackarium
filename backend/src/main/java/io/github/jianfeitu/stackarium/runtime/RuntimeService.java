package io.github.jianfeitu.stackarium.runtime;

import io.github.jianfeitu.stackarium.common.ApiException;
import io.github.jianfeitu.stackarium.project.ProjectService;
import io.github.jianfeitu.stackarium.runtime.DockerCommandExecutor.Operation;
import io.github.jianfeitu.stackarium.topology.TopologyService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RuntimeService {
    public record Logs(UUID nodeId, Instant observedAt, String stdout, String stderr) {}

    private final ProjectService projects;
    private final TopologyService topologies;
    private final RuntimePlanGenerator plans;
    private final ComposeGenerator generator;
    private final RuntimeRepository repository;
    private final DockerCommandExecutor docker;
    private final DockerObserver observer;
    private final RuntimeEventHub events;
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final Map<UUID, Map<UUID, String>> lastComponentStates = new ConcurrentHashMap<>();

    public RuntimeService(ProjectService projects, TopologyService topologies, RuntimePlanGenerator plans,
                          ComposeGenerator generator, RuntimeRepository repository,
                          DockerCommandExecutor docker, DockerObserver observer, RuntimeEventHub events) {
        this.projects = projects;
        this.topologies = topologies;
        this.plans = plans;
        this.generator = generator;
        this.repository = repository;
        this.docker = docker;
        this.observer = observer;
        this.events = events;
    }

    public synchronized RuntimeView generate(UUID projectId) {
        projects.get(projectId);
        repository.latest(projectId).ifPresent(previous -> {
            if (previous.status() != RuntimeStatus.STOPPED)
                throw new ApiException(HttpStatus.CONFLICT, "RUNTIME_ACTIVE",
                        "请先停止当前运行环境，再生成新的环境");
        });
        RuntimePlan plan = plans.generate(topologies.get(projectId));
        UUID id = UUID.randomUUID();
        String composeName = "stackarium-" + id.toString().replace("-", "").substring(0, 12);
        try {
            Path workspace = generator.generate(plan, id);
            Instant now = Instant.now();
            RuntimeInstance instance = new RuntimeInstance(id, projectId, plan.topologyRevision(), composeName,
                    workspace.toString(), plan, RuntimeStatus.GENERATED, null, null, null,
                    now, null, null, now);
            DockerCommandExecutor.Result config = docker.execute(instance, Operation.CONFIG);
            if (!config.ok()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COMPOSE",
                    "生成的 Compose 配置未通过验证：" + config.errorSummary());
            repository.insert(instance);
            events.publish(projectId, id, "runtime.generated", null,
                    Map.of("status", RuntimeStatus.GENERATED.name(), "revision", Long.toString(plan.topologyRevision())));
            return view(instance, List.of());
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RUNTIME_GENERATE_FAILED",
                    "生成运行环境失败：" + safeReason(error));
        }
    }

    public synchronized RuntimeView start(UUID projectId, UUID runtimeId) {
        RuntimeInstance instance = find(projectId, runtimeId);
        if (instance.status() != RuntimeStatus.GENERATED && instance.status() != RuntimeStatus.STOPPED)
            throw new ApiException(HttpStatus.CONFLICT, "RUNTIME_STATE", "当前运行环境不能启动");
        if (instance.topologyRevision() != topologies.get(projectId).revision())
            throw new ApiException(HttpStatus.CONFLICT, "RUNTIME_STALE", "架构已修改，请重新生成运行环境");
        transition(instance, RuntimeStatus.PREPARING, null, null, null);
        workers.execute(() -> {
            try {
                DockerCommandExecutor.Result result = docker.execute(instance, Operation.UP);
                if (!result.ok()) { fail(instance, "START", null, result.errorSummary()); return; }
                transition(instance, RuntimeStatus.STARTING, null, null, null);
                reconcile(instance);
            } catch (Exception error) { fail(instance, "START", null, safeReason(error)); }
        });
        return view(find(projectId, runtimeId), List.of());
    }

    public synchronized RuntimeView stop(UUID projectId, UUID runtimeId) {
        RuntimeInstance instance = find(projectId, runtimeId);
        if (instance.status() == RuntimeStatus.STOPPED)
            throw new ApiException(HttpStatus.CONFLICT, "RUNTIME_STATE", "运行环境已经停止");
        if (instance.status() == RuntimeStatus.PREPARING)
            throw new ApiException(HttpStatus.CONFLICT, "RUNTIME_STATE", "正在准备容器，请等待启动命令完成后再停止");
        if (instance.status() == RuntimeStatus.STOPPING)
            throw new ApiException(HttpStatus.CONFLICT, "RUNTIME_STATE", "运行环境正在停止");
        transition(instance, RuntimeStatus.STOPPING, null, null, null);
        workers.execute(() -> {
            try {
                DockerCommandExecutor.Result result = docker.execute(instance, Operation.DOWN);
                if (!result.ok()) { fail(instance, "STOP", null, result.errorSummary()); return; }
                transition(instance, RuntimeStatus.STOPPED, null, null, null);
            } catch (Exception error) { fail(instance, "STOP", null, safeReason(error)); }
        });
        return view(find(projectId, runtimeId), List.of());
    }

    public Optional<RuntimeView> latest(UUID projectId) {
        projects.get(projectId);
        return repository.latest(projectId).map(this::inspect);
    }

    public RuntimeView get(UUID projectId, UUID runtimeId) { return inspect(find(projectId, runtimeId)); }

    public List<RuntimeEvent> events(UUID projectId, UUID runtimeId) {
        find(projectId, runtimeId);
        return repository.events(runtimeId);
    }

    public Logs logs(UUID projectId, UUID runtimeId, UUID nodeId, int lines) {
        RuntimeInstance instance = find(projectId, runtimeId);
        String service = instance.plan().services().stream().filter(item -> item.nodeId().equals(nodeId))
                .findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "RUNTIME_COMPONENT_NOT_FOUND", "运行组件不存在")).serviceName();
        try {
            DockerCommandExecutor.Result result = docker.execute(instance, Operation.LOGS, service, lines);
            if (!result.ok()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "RUNTIME_LOGS_FAILED", "读取容器日志失败：" + result.errorSummary());
            return new Logs(nodeId, Instant.now(), result.stdout(), result.stderr());
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RUNTIME_LOGS_FAILED",
                    "读取容器日志失败：" + safeReason(error));
        }
    }

    @Scheduled(fixedDelay = 3000)
    public void poll() {
        for (RuntimeInstance instance : repository.watchable()) {
            try { reconcile(instance); }
            catch (Exception error) { fail(instance, "INSPECT", null, safeReason(error)); }
        }
    }

    @PreDestroy
    public void shutdown() { workers.shutdown(); }

    private RuntimeView inspect(RuntimeInstance instance) {
        try { return reconcile(instance); }
        catch (Exception error) {
            fail(instance, "INSPECT", null, safeReason(error));
            return view(find(instance.projectId(), instance.id()), List.of());
        }
    }

    private synchronized RuntimeView reconcile(RuntimeInstance instance) throws IOException, InterruptedException {
        RuntimeInstance current = find(instance.projectId(), instance.id());
        List<ContainerObservation> observations = observer.observe(current);
        if (current.status() != RuntimeStatus.STOPPING && current.status() != RuntimeStatus.PREPARING) {
            RuntimeStatus observed = derive(current, observations);
            if (observed != current.status()) {
                ContainerObservation failed = observations.stream()
                        .filter(item -> item.state().equals("exited") || item.state().equals("dead"))
                        .findFirst().orElse(null);
                String phase = observed == RuntimeStatus.FAILED ? "HEALTH" : null;
                String reason = observed == RuntimeStatus.FAILED
                        ? failed == null ? "容器未在预期时间内启动" : "组件容器已退出：" + failed.displayName()
                        : null;
                transition(current, observed, phase, failed == null ? null : failed.nodeId(), reason);
                current = find(instance.projectId(), instance.id());
            }
        }
        Map<UUID, String> previous = lastComponentStates.computeIfAbsent(current.id(), ignored -> new ConcurrentHashMap<>());
        for (ContainerObservation component : observations) {
            String state = component.state() + "/" + component.health();
            String old = previous.put(component.nodeId(), state);
            if (!state.equals(old) && !state.equals("missing/none"))
                events.publish(current.projectId(), current.id(), "component.status.changed",
                        component.nodeId(), Map.of("state", component.state(), "health", component.health(),
                                "componentType", component.componentType()));
        }
        return view(current, observations);
    }

    static RuntimeStatus derive(RuntimeInstance current, List<ContainerObservation> components) {
        if (current.status() == RuntimeStatus.STOPPING) return RuntimeStatus.STOPPING;
        boolean allMissing = components.stream().allMatch(item -> item.state().equals("missing"));
        if (allMissing) {
            if (current.status() == RuntimeStatus.RUNNING || current.status() == RuntimeStatus.DEGRADED)
                return RuntimeStatus.STOPPED;
            if (current.status() == RuntimeStatus.STARTING && Duration.between(current.updatedAt(), Instant.now()).toSeconds() > 60)
                return RuntimeStatus.FAILED;
            return current.status();
        }
        if (components.stream().anyMatch(item -> List.of("exited", "dead").contains(item.state())))
            return RuntimeStatus.FAILED;
        if (components.stream().anyMatch(item -> item.health().equals("unhealthy") || item.state().equals("missing")))
            return RuntimeStatus.DEGRADED;
        if (components.stream().allMatch(item -> item.state().equals("running") && item.health().equals("healthy")))
            return RuntimeStatus.RUNNING;
        return RuntimeStatus.STARTING;
    }

    private void transition(RuntimeInstance instance, RuntimeStatus status, String phase, UUID nodeId, String message) {
        repository.status(instance.id(), status, phase, nodeId, message);
        events.publish(instance.projectId(), instance.id(), "runtime.status.changed", nodeId,
                Map.of("status", status.name(), "phase", phase == null ? "" : phase,
                        "reason", message == null ? "" : message));
    }

    private void fail(RuntimeInstance instance, String phase, UUID nodeId, String reason) {
        RuntimeInstance current = find(instance.projectId(), instance.id());
        if (current.status() == RuntimeStatus.STOPPED) return;
        if (current.status() == RuntimeStatus.FAILED && phase.equals(current.errorPhase())) return;
        transition(current, RuntimeStatus.FAILED, phase, nodeId, reason);
    }

    private RuntimeInstance find(UUID projectId, UUID runtimeId) {
        return repository.find(projectId, runtimeId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "RUNTIME_NOT_FOUND", "运行环境不存在"));
    }

    private static RuntimeView view(RuntimeInstance instance, List<ContainerObservation> components) {
        return new RuntimeView(instance.id(), instance.projectId(), instance.topologyRevision(), instance.status(),
                instance.errorPhase(), instance.errorNodeId(), instance.errorMessage(), instance.createdAt(),
                instance.startedAt(), instance.stoppedAt(), Instant.now(), components);
    }

    private static String safeReason(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return "外部命令未提供错误原因";
        return message.length() > 350 ? message.substring(0, 350) : message;
    }
}
