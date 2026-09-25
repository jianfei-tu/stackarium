package io.github.jianfeitu.stackarium.notification;

import io.github.jianfeitu.stackarium.experiment.EventReporter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class NotificationApplication {
    public static void main(String[] args) { SpringApplication.run(NotificationApplication.class, args); }
    @Bean EventReporter eventReporter() { return new EventReporter(); }
}
