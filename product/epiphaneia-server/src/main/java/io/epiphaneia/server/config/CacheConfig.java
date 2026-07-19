package io.epiphaneia.server.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
    // Auto-configured by spring-boot-starter-cache + Caffeine on classpath
}
