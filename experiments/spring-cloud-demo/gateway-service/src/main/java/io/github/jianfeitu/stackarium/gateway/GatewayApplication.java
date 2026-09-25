package io.github.jianfeitu.stackarium.gateway;

import io.github.jianfeitu.stackarium.experiment.EventReporter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) { SpringApplication.run(GatewayApplication.class, args); }
    @Bean EventReporter eventReporter() { return new EventReporter(); }
}
