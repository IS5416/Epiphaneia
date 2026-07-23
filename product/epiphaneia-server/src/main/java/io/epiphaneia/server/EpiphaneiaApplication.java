package io.epiphaneia.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ComponentScan(basePackages = "io.epiphaneia")
@EnableJpaRepositories(basePackages = "io.epiphaneia.agent.api.repository")
@ConfigurationPropertiesScan(basePackages = "io.epiphaneia.infra.api.config")
@EnableAsync
public class EpiphaneiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(EpiphaneiaApplication.class, args);
    }
}
