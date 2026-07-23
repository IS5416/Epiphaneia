package io.epiphaneia.server.security;

import io.epiphaneia.domain.internal.entity.ApiToken;
import io.epiphaneia.domain.internal.entity.Admin;
import io.epiphaneia.domain.internal.repository.ApiTokenRepository;
import io.epiphaneia.domain.internal.repository.AdminRepository;
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
    private final AdminRepository adminRepository;

    public BearerTokenFilter(ApiTokenRepository apiTokenRepository, AdminRepository adminRepository) {
        this.apiTokenRepository = apiTokenRepository;
        this.adminRepository = adminRepository;
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

        // ponytail: ApiToken entity doesn't expose getAdmin() getter — the admin FK is internal.
        // We just need any admin principal for Spring Security; use a placeholder.
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", rawToken, ADMIN_AUTHORITY);
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }

}
