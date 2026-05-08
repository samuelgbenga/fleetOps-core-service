package com.fleetops.core.config;

import com.fleetops.core.user.entity.User;
import com.fleetops.core.user.enums.UserRole;
import com.fleetops.core.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin.name:System Admin}")
    private String adminName;

    @Value("${app.seed.admin.email:admin@fleetops.com}")
    private String adminEmail;

    @Value("${app.seed.admin.password:Admin@1234}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin user already exists — skipping seed.");
            return;
        }

        User admin = User.builder()
                .name(adminName)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role(UserRole.ADMIN)
                .build();

        userRepository.save(admin);
        log.info("Seeded admin user: {}", adminEmail);
    }
}
