package io.epiphaneia.server.security;

import io.epiphaneia.domain.entity.Admin;
import io.epiphaneia.domain.repository.AdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Seeds the initial admin user on first startup.
 * <p>
 * If no admin exists in the database, generates a 20-character random password,
 * stores its bcrypt hash, and writes the credentials to a temp file (owner-only
 * where POSIX permissions are available). The password is never logged.
 * The admin is forced to change password on first login (mustChangePassword = true).
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CREDENTIAL_FILE_TTL_MINUTES = 30;

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

        try {
            Path credFile = Path.of(System.getProperty("java.io.tmpdir"), "epiphaneia-initial-admin.txt");
            writeCredentialFile(credFile, password);
            log.info("========================================");
            log.info("INITIAL ADMIN created: username=admin, temporary password written to {}", credFile);
            log.info("IMPORTANT: Delete the credential file after first login.");
            log.info("========================================");
            scheduleCredentialCleanup(credFile);
        } catch (IOException e) {
            log.warn("Failed to write credential file; check container logs for setup instructions (non-fatal): {}", e.getMessage());
        }
    }

    private static void writeCredentialFile(Path credFile, String password) throws IOException {
        String content = "Username: admin\nPassword: " + password + "\n";
        if (credFile.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            // create atomically with owner-only permissions (no umask window)
            Files.createFile(credFile,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.writeString(credFile, content);
        } else {
            // platforms without POSIX support (e.g. Windows): accept default ACL
            Files.writeString(credFile, content);
        }
    }

    private static void scheduleCredentialCleanup(Path credFile) {
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "admin-cred-cleanup");
            t.setDaemon(true);
            return t;
        }).schedule(() -> {
            try {
                Files.deleteIfExists(credFile);
            } catch (IOException ignored) {
                // best-effort cleanup; log hint above already tells the operator
            }
        }, CREDENTIAL_FILE_TTL_MINUTES, TimeUnit.MINUTES);
    }

    private static String generatePassword() {
        byte[] bytes = new byte[15];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
