package co.edu.escuelaing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Collections;

@SpringBootApplication
public class RestServiceApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(RestServiceApplication.class);
        String port = System.getenv().getOrDefault("PORT", "6000");
        app.setDefaultProperties(Collections.singletonMap("server.port", port));
        app.run(args);
    }
}
