package io.github.jianfeitu.stackarium.experiment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

/** Public experiment-to-platform event contract. It has no platform implementation dependency. */
public final class EventReporter {
    private record Event(UUID nodeId, Instant occurredAt, String eventType, Map<String, String> payload) {}

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final URI endpoint;
    private final String token;
    private final UUID nodeId;

    public EventReporter() {
        endpoint = URI.create(required("STACKARIUM_EVENT_URL"));
        token = required("STACKARIUM_EVENT_TOKEN");
        nodeId = UUID.fromString(required("STACKARIUM_NODE_ID"));
    }

    public void publish(String type, Map<String, String> payload) {
        String body = json.writeValueAsString(new Event(nodeId, Instant.now(), type, payload));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json")
                .header("X-Stackarium-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    if (error != null || response.statusCode() != 202)
                        System.err.println("Stackarium event delivery failed: " + type);
                });
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing experiment event configuration: " + key);
        return value;
    }
}
