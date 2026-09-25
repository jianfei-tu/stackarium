package io.github.jianfeitu.stackarium.notification;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class NotificationMessaging {
    @Bean TopicExchange ordersExchange() { return new TopicExchange("stackarium.orders", true, false); }
    @Bean Queue orderCreatedQueue() { return new Queue("stackarium.order-created", true); }
    @Bean Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(ordersExchange).with("order.created");
    }
}
