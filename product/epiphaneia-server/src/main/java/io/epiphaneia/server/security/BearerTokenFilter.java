package io.epiphaneia.server.security;

import io.epiphaneia.domain.entity.ApiToken;
import io.epiphaneia.domain.repository.ApiTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Extracts Bearer token from the Authorization header, validates against
 * the stored SHA-256 token hash, and sets the Spring Security context.
 * <p>
 * Token format: "epi_" prefix + 32 random characters.
 * Falls through to the next filter if no Bearer token is present or
 * the token is invalid/revoked.
 */
public class BearerTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<SimpleGrantedAuthority> ADMIN_AUTHORITY =
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));

    private final ApiTokenRepository apiTokenRepository;

    public BearerTokenFilter(ApiTokenRepository apiTokenRepository) {
        this.apiTokenRepository = apiTokenRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Skip if already authenticated by session
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String rawToken = header.substring(BEARER_PREFIX.length()).trim();
        String tokenHash = TokenHasher.sha256(rawToken);

        Optional<ApiToken> token = apiTokenRepository.findByTokenHash(tokenHash);
        if (token.isEmpty() || !token.get().isValid()) {
            chain.doFilter(request, response);
            return;
        }

        // Principal is the real admin identity (UUID). Accessing the lazy @ManyToOne proxy's
        // id() does not trigger a DB hit, so no LazyInitializationException outside a session.
        // Credentials stay null: the raw token never enters the security context.
        var admin = token.get().getAdmin();
        if (admin == null) {
            // orphaned token (admin deleted without cascade): treat as unauthenticated
            chain.doFilter(request, response);
            return;
        }
        var auth = new UsernamePasswordAuthenticationToken(admin.getId(), null, ADMIN_AUTHORITY);
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }

}
