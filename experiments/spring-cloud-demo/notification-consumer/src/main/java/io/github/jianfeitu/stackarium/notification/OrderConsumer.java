package io.github.jianfeitu.stackarium.notification;

import io.github.jianfeitu.stackarium.experiment.EventReporter;
import io.github.jianfeitu.stackarium.experiment.OrderCreated;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class OrderConsumer {
    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);
    private final ObjectMapper json;
    private final EventReporter events;

    public OrderConsumer(ObjectMapper json, EventReporter events) {
        this.json = json;
        this.events = events;
    }

    @RabbitListener(queues = "stackarium.order-created")
    public void received(String body) {
        OrderCreated order = json.readValue(body, OrderCreated.class);
        log.info("Consumed OrderCreated {}", order.messageId());
        events.publish("MESSAGE_CONSUMED", Map.of("traceId", order.traceId(),
                "messageId", order.messageId(), "routingKey", "order.created",
                "consumer", "notification-consumer"));
    }
}
