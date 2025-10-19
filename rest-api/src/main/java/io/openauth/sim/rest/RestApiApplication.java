package io.openauth.sim.rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class RestApiApplication {

    static ConfigurableApplicationContext launch(String... args) {
        return SpringApplication.run(RestApiApplication.class, args);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
