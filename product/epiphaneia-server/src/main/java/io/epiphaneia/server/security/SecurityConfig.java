package io.epiphaneia.server.security;

import io.epiphaneia.domain.internal.repository.AdminRepository;
import io.epiphaneia.domain.internal.repository.ApiTokenRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AdminRepository adminRepository;
    private final ApiTokenRepository apiTokenRepository;

    public SecurityConfig(AdminRepository adminRepository, ApiTokenRepository apiTokenRepository) {
        this.adminRepository = adminRepository;
        this.apiTokenRepository = apiTokenRepository;
    }

    // ponytail: prevents Spring Boot auto-config from creating default 'user' user
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> { throw new org.springframework.security.core.userdetails
                .UsernameNotFoundException("No default user"); };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // ponytail: these are @Bean-annotated to support constructor injection in filter
        var sessionFilter = new SessionAuthFilter(adminRepository);
        var bearerFilter = new BearerTokenFilter(apiTokenRepository, adminRepository);
        var rateLimitFilter = new RateLimitFilter();

        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(sessionFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/system/status").permitAll()
                // Everything else requires authentication
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().permitAll()
            )
            .build();
    }
}
