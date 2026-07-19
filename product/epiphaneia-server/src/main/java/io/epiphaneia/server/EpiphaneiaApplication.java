package io.epiphaneia.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "io.epiphaneia")
@EnableJpaRepositories(basePackages = "io.epiphaneia.agent.api.repository")
@EntityScan(basePackages = "io.epiphaneia.agent.api.model")
@EnableAsync
public class EpiphaneiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(EpiphaneiaApplication.class, args);
    }
}
