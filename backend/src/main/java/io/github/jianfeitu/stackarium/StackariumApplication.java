package io.github.jianfeitu.stackarium;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StackariumApplication {
    public static void main(String[] args) {
        SpringApplication.run(StackariumApplication.class, args);
    }
}
