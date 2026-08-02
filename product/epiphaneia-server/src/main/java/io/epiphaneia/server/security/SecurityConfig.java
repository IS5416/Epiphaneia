package io.epiphaneia.server.security;

import io.epiphaneia.domain.repository.AdminRepository;
import io.epiphaneia.domain.repository.ApiTokenRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

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
        var bearerFilter = new BearerTokenFilter(apiTokenRepository);
        var rateLimitFilter = new RateLimitFilter();

        // CSRF enabled: the SPA authenticates with session cookies (same-origin via Nginx).
        // CookieCsrfTokenRepository serves XSRF-TOKEN; plain handler accepts the cookie value
        // verbatim in the X-XSRF-TOKEN header (client.ts already sends it). Login is exempt —
        // no session/XSRF cookie exists on the very first visit.
        return http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/api/v1/auth/login"))
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
