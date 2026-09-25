package io.github.jianfeitu.stackarium.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

@Component
public class ComposeGenerator {
    private final RuntimeArtifacts artifacts;
    private final Path experimentRoot;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public ComposeGenerator(RuntimeArtifacts artifacts,
                            @Value("${stackarium.experiment.root:../experiments/spring-cloud-demo}") String experimentRoot) {
        this.artifacts = artifacts;
        this.experimentRoot = Path.of(experimentRoot).toAbsolutePath().normalize();
    }

    public ComposeGenerator(RuntimeArtifacts artifacts) { this(artifacts, "../experiments/spring-cloud-demo"); }

    public Path generate(RuntimePlan plan, UUID runtimeId) throws IOException {
        Path workspace = artifacts.create(plan.projectId(), runtimeId);
        Map<String, Object> services = new LinkedHashMap<>();
        Map<String, Object> volumes = new LinkedHashMap<>();
        for (RuntimePlan.Service service : plan.services()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("image", service.image());
            entry.put("restart", "no");
            Map<String, String> environment = new LinkedHashMap<>(service.environment());
            if (service.buildModule() != null) {
                if (!List.of("gateway-service", "order-service", "inventory-service",
                        "notification-consumer").contains(service.buildModule())
                        || !Files.isRegularFile(experimentRoot.resolve("Dockerfile")))
                    throw new IllegalArgumentException("Invalid experiment build module");
                entry.put("build", Map.of("context", experimentRoot.toString(), "dockerfile", "Dockerfile",
                        "args", Map.of("MODULE", service.buildModule())));
                environment.put("STACKARIUM_EVENT_URL", "http://host.docker.internal:8080/api/v1/projects/"
                        + plan.projectId() + "/runtime/" + runtimeId + "/experiment-events");
                environment.put("STACKARIUM_EVENT_TOKEN", "${STACKARIUM_EVENT_TOKEN}");
                environment.put("STACKARIUM_NODE_ID", service.nodeId().toString());
                entry.put("extra_hosts", List.of("host.docker.internal:host-gateway"));
            }
            if (!environment.isEmpty()) entry.put("environment", environment);
            if (service.publishedPort() != null) {
                if (service.publishedPort() != 18080 || !"gateway-service".equals(service.buildModule()))
                    throw new IllegalArgumentException("Invalid published experiment port");
                entry.put("ports", List.of("127.0.0.1:18080:8080"));
            }
            entry.put("expose", service.ports().stream().map(String::valueOf).toList());
            entry.put("networks", plan.networks());
            List<String> mounts = new ArrayList<>();
            for (Map.Entry<String, String> volume : service.volumes().entrySet()) {
                String source = volume.getKey();
                if (source.startsWith("file:")) {
                    String filename = source.substring(5);
                    if (!filename.matches("[a-zA-Z0-9._-]+") || !service.files().containsKey(filename))
                        throw new IllegalArgumentException("Invalid generated file reference");
                    String generated = service.serviceName() + "-" + filename;
                    Files.writeString(workspace.resolve(generated), service.files().get(filename),
                            StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                    mounts.add("./" + generated + ":" + volume.getValue());
                } else {
                    if (!source.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid volume name");
                    String named = service.serviceName() + "-" + source;
                    volumes.put(named, Map.of());
                    mounts.add(named + ":" + volume.getValue());
                }
            }
            if (!mounts.isEmpty()) entry.put("volumes", mounts);
            if (!service.dependencies().isEmpty()) {
                Map<String, Object> dependencies = new LinkedHashMap<>();
                for (String dependency : service.dependencies())
                    dependencies.put(dependency, Map.of("condition", "service_healthy"));
                entry.put("depends_on", dependencies);
            }
            entry.put("healthcheck", Map.of("test", service.healthcheck(), "interval", "5s",
                    "timeout", "3s", "retries", 20, "start_period", service.startPeriodSeconds() + "s"));
            services.put(service.serviceName(), entry);
        }
        Map<String, Object> compose = new LinkedHashMap<>();
        compose.put("services", services);
        compose.put("networks", Map.of("stackarium", Map.of()));
        if (!volumes.isEmpty()) compose.put("volumes", volumes);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        Files.writeString(workspace.resolve("docker-compose.yml"), new Yaml(options).dump(compose),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        byte[] secret = new byte[24];
        random.nextBytes(secret);
        byte[] eventToken = new byte[32];
        random.nextBytes(eventToken);
        byte[] nacosToken = new byte[32];
        random.nextBytes(nacosToken);
        byte[] nacosIdentity = new byte[24];
        random.nextBytes(nacosIdentity);
        Files.writeString(workspace.resolve(".env"),
                "MYSQL_ROOT_PASSWORD=" + Base64.getUrlEncoder().withoutPadding().encodeToString(secret) + "\n"
                + "STACKARIUM_EVENT_TOKEN=" + Base64.getUrlEncoder().withoutPadding().encodeToString(eventToken) + "\n"
                + "NACOS_AUTH_TOKEN=" + Base64.getEncoder().encodeToString(nacosToken) + "\n"
                + "NACOS_AUTH_IDENTITY_VALUE=" + Base64.getUrlEncoder().withoutPadding().encodeToString(nacosIdentity) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return workspace;
    }
}
