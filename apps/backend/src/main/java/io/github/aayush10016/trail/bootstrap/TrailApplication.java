package io.github.aayush10016.trail.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstraps the Trail backend application.
 */
@SpringBootApplication(scanBasePackages = "io.github.aayush10016.trail")
public class TrailApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrailApplication.class, args);
    }
}

