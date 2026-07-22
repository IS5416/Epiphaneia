package io.epiphaneia.server.config;

import jakarta.persistence.Entity;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.orm.jpa.persistenceunit.MutablePersistenceUnitInfo;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitPostProcessor;

/**
 * JPA entity scan configuration for Spring Boot 4.1.0.
 * <p>
 * SB 4.1.0 removed {@code @EntityScan} and restructured autoconfigure packages.
 * This customizer performs classpath scanning to register entities from
 * {@code io.epiphaneia.agent.api.model} which lives outside the default
 * scan path of the server module.
 */
@Configuration
public class JpaConfig {

    private static final String ENTITY_PACKAGE = "io.epiphaneia.agent.api.model";

    @Bean
    public EntityManagerFactoryBuilderCustomizer entityScanCustomizer() {
        return builder -> {
            var scanner = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            var entityClasses = scanner.findCandidateComponents(ENTITY_PACKAGE).stream()
                    .map(org.springframework.beans.factory.config.BeanDefinition::getBeanClassName)
                    .toList();

            builder.setPersistenceUnitPostProcessors((PersistenceUnitPostProcessor) pui -> {
                if (pui instanceof MutablePersistenceUnitInfo mPui) {
                    entityClasses.forEach(mPui::addManagedClassName);
                }
            });
        };
    }
}
