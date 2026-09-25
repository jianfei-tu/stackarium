package io.github.jianfeitu.stackarium.inventory;

import io.github.jianfeitu.stackarium.experiment.EventReporter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class InventoryApplication {
    public static void main(String[] args) { SpringApplication.run(InventoryApplication.class, args); }
    @Bean EventReporter eventReporter() { return new EventReporter(); }
}
