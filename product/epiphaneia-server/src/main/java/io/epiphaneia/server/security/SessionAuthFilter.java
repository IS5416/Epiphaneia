package io.epiphaneia.server.security;

import io.epiphaneia.agent.api.model.Admin;
import io.epiphaneia.agent.api.repository.AdminRepository;
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
 * Extracts the JSESSIONID from the request, looks up the authenticated session,
 * and sets the Spring Security context for session-authenticated requests.
 * <p>
 * Only activates for requests that have a valid HTTP session containing the
 * "ADMIN_ID" attribute set during login. Falls through to the next filter
 * (BearerTokenFilter) for unauthenticated requests.
 */
public class SessionAuthFilter extends OncePerRequestFilter {

    private static final String ADMIN_ID_ATTR = "ADMIN_ID";
    private static final List<SimpleGrantedAuthority> ADMIN_AUTHORITY =
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));

    private final AdminRepository adminRepository;

    public SessionAuthFilter(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var session = request.getSession(false);
        if (session == null) {
            chain.doFilter(request, response);
            return;
        }

        Object adminId = session.getAttribute(ADMIN_ID_ATTR);
        if (adminId == null) {
            chain.doFilter(request, response);
            return;
        }

        Optional<Admin> admin = adminRepository.findById((java.util.UUID) adminId);
        if (admin.isEmpty()) {
            session.invalidate();
            chain.doFilter(request, response);
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                admin.get(), null, ADMIN_AUTHORITY);
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }
}
