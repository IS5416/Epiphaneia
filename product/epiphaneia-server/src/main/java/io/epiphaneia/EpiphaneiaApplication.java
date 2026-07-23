package io.epiphaneia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "io.epiphaneia")
@EnableJpaRepositories(basePackages = "io.epiphaneia.domain.internal.repository")
@ConfigurationPropertiesScan(basePackages = "io.epiphaneia.infra.api.config")
@EnableAsync
public class EpiphaneiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(EpiphaneiaApplication.class, args);
    }
}
