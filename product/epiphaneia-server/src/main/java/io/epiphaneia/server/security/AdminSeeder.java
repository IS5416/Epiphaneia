package io.epiphaneia.server.security;

import io.epiphaneia.domain.internal.entity.Admin;
import io.epiphaneia.domain.internal.repository.AdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Seeds the initial admin user on first startup.
 * <p>
 * If no admin exists in the database, generates a 16-character random password,
 * stores its bcrypt hash, and logs the initial credentials to the console.
 * The admin is forced to change password on first login (mustChangePassword = true).
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminRepository adminRepository;

    public AdminSeeder(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminRepository.existsByUsername("admin")) {
            log.info("Admin user already exists, skipping seed.");
            return;
        }

        String password = generatePassword();
        Admin admin = new Admin();
        admin.setPasswordHash(ENCODER.encode(password));

        adminRepository.save(admin);

        log.info("========================================");
        log.info("INITIAL ADMIN: username=admin password={}", password);
        log.info("========================================");

        try {
            Path credFile = Path.of(System.getProperty("java.io.tmpdir"), "epiphaneia-initial-admin.txt");
            Files.writeString(credFile, "Username: admin\nPassword: " + password + "\n");
        } catch (java.io.IOException e) {
            log.warn("Failed to write credential file (non-fatal): {}", e.getMessage());
        }
    }

    private static String generatePassword() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
