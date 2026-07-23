package io.epiphaneia.server.controller;

import io.epiphaneia.domain.internal.entity.Admin;
import io.epiphaneia.domain.internal.entity.ApiToken;
import io.epiphaneia.domain.internal.repository.AdminRepository;
import io.epiphaneia.domain.internal.repository.ApiTokenRepository;
import io.epiphaneia.server.dto.*;
import io.epiphaneia.server.mapper.ApiTokenMapper;
import io.epiphaneia.server.security.TokenHasher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TOKEN_PREFIX = "epi_";

    private final AdminRepository adminRepo;
    private final ApiTokenRepository tokenRepo;
    private final ApiTokenMapper tokenMapper;

    public AuthController(AdminRepository adminRepo, ApiTokenRepository tokenRepo,
                          ApiTokenMapper tokenMapper) {
        this.adminRepo = adminRepo;
        this.tokenRepo = tokenRepo;
        this.tokenMapper = tokenMapper;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest req,
                                                            HttpServletRequest request, HttpSession session) {
        Admin admin = adminRepo.findByUsername(req.username())
                .orElse(null);

        if (admin == null || !ENCODER.matches(req.password(), admin.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("INVALID_CREDENTIALS", "Invalid credentials"));
        }

        session.invalidate();
        HttpSession newSession = request.getSession(true);
        newSession.setAttribute("ADMIN_ID", admin.getId());

        return ResponseEntity.ok(ApiResponse.ok(
                new LoginResponse(null, null, admin.isMustChangePassword())));
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req, HttpSession session) {
        UUID adminId = (UUID) session.getAttribute("ADMIN_ID");
        if (adminId == null) throw new IllegalStateException("Not authenticated");

        Admin admin = adminRepo.findById(adminId)
                .orElseThrow(() -> new IllegalStateException("Admin not found"));

        if (!ENCODER.matches(req.currentPassword(), admin.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        admin.setPasswordHash(ENCODER.encode(req.newPassword()));
        admin.setMustChangePassword(false);
        adminRepo.save(admin);
    }

    @GetMapping("/tokens")
    public ApiResponse<List<ApiTokenResponse>> listTokens() {
        // ponytail: single admin — list all tokens. Phase 3C adds admin context from session.
        List<ApiTokenResponse> tokens = tokenRepo.findAll().stream()
                .filter(ApiToken::isValid)
                .map(tokenMapper::toResponse)
                .toList();
        return ApiResponse.ok(tokens);
    }

    @PostMapping("/tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateApiTokenResponse> createToken(
            @Valid @RequestBody CreateApiTokenRequest req, HttpSession session) {
        UUID adminId = (UUID) session.getAttribute("ADMIN_ID");
        Admin admin = adminRepo.findById(adminId)
                .orElseThrow(() -> new IllegalStateException("Admin not found"));

        String rawToken = TOKEN_PREFIX + generateRandomString(32);

        ApiToken token = new ApiToken();
        token.setName(req.name());
        token.setPrefix(rawToken.substring(0, 12));
        token.setTokenHash(TokenHasher.sha256(rawToken));
        token.setAdmin(admin);
        tokenRepo.save(token);

        return ApiResponse.ok(new CreateApiTokenResponse(
                token.getId(), token.getName(), rawToken, token.getPrefix(), token.getCreatedAt()));
    }

    @DeleteMapping("/tokens/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeToken(@PathVariable UUID id) {
        ApiToken token = tokenRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Token not found"));
        token.setRevokedAt(Instant.now());
        tokenRepo.save(token);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpSession session) {
        session.invalidate();
    }

    private static String generateRandomString(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
    }
}
