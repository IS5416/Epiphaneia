package io.epiphaneia.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "io.epiphaneia")
@ConfigurationPropertiesScan(basePackages = "io.epiphaneia.infra.internal.config")
@EnableAsync
public class EpiphaneiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(EpiphaneiaApplication.class, args);
    }
}
