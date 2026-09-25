package io.github.jianfeitu.stackarium.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class DockerCommandExecutor {
    public enum Operation { CONFIG, UP, DOWN, PS, LOGS }
    public record Result(int exitCode, String stdout, String stderr) {
        public boolean ok() { return exitCode == 0; }
        public String errorSummary() {
            String source = stderr.isBlank() ? stdout : stderr;
            String safe = source.replaceAll("(?i)\\b([A-Z0-9_]*(?:PASSWORD|SECRET|TOKEN|API_KEY|CREDENTIAL)[A-Z0-9_]*)\\s*[:=]\\s*([^\\s,;]+)", "$1=[隐藏]")
                    .replaceAll("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+", "Bearer [隐藏]");
            return safe.length() > 350 ? safe.substring(safe.length() - 350) : safe;
        }
    }

    private final RuntimeArtifacts artifacts;

    public DockerCommandExecutor(RuntimeArtifacts artifacts) { this.artifacts = artifacts; }

    public Result execute(RuntimeInstance instance, Operation operation) throws IOException, InterruptedException {
        return execute(instance, operation, null, 0);
    }

    public Result execute(RuntimeInstance instance, Operation operation, String serviceName, int lines)
            throws IOException, InterruptedException {
        Path workspace = artifacts.verified(instance);
        String expectedProject = "stackarium-" + instance.id().toString().replace("-", "").substring(0, 12);
        if (!expectedProject.equals(instance.composeProjectName()))
            throw new IllegalArgumentException("Invalid Compose project name");
        if (!java.nio.file.Files.isRegularFile(workspace.resolve("docker-compose.yml")))
            throw new IOException("Compose artifact is missing");
        List<String> command = command(instance, operation, serviceName, lines, expectedProject);
        Duration timeout = switch (operation) {
            case CONFIG, PS, LOGS -> Duration.ofSeconds(30);
            case UP -> Duration.ofMinutes(20);
            case DOWN -> Duration.ofMinutes(3);
        };
        Process process = new ProcessBuilder(command).directory(workspace.toFile()).start();
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> capture(process.getInputStream()));
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> capture(process.getErrorStream()));
        boolean done = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!done) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            return new Result(-1, stdout.join(), "Docker 命令超时：" + operation);
        }
        return new Result(process.exitValue(), stdout.join(), stderr.join());
    }

    static List<String> command(RuntimeInstance instance, Operation operation, String serviceName,
                                int lines, String expectedProject) {
        List<String> command = new ArrayList<>(List.of("docker", "compose", "--project-name", expectedProject,
                "--file", "docker-compose.yml"));
        switch (operation) {
            case CONFIG -> command.addAll(List.of("config", "--quiet"));
            case UP -> command.addAll(List.of("up", "--build", "--detach"));
            case DOWN -> command.add("down");
            case PS -> command.addAll(List.of("ps", "--all", "--format", "json"));
            case LOGS -> {
                if (lines < 1 || lines > 300 || instance.plan().services().stream()
                        .noneMatch(service -> service.serviceName().equals(serviceName)))
                    throw new IllegalArgumentException("Invalid log request");
                command.addAll(List.of("logs", "--no-color", "--tail", Integer.toString(lines), serviceName));
            }
            default -> throw new IllegalArgumentException("Unsupported Docker operation");
        }
        return command;
    }

    private static String capture(InputStream stream) {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
                if (output.length() > 32000) output.delete(0, output.length() - 32000);
            }
        } catch (IOException error) {
            return "读取 Docker 输出失败";
        }
        return output.toString();
    }
}
