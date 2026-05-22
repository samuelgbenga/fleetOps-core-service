package com.fleetops.core.shared.config;

import com.fleetops.core.module.user.model.Role;
import com.fleetops.core.module.user.model.User;
import com.fleetops.core.module.user.model.UserType;
import com.fleetops.core.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
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
            log.info("Platform admin already exists — skipping seed");
            return;
        }

        var admin = User.builder()
                .name(adminName)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.PLATFORM_ADMIN)
                .userType(UserType.PLATFORM)
                .active(true)
                .totalJobsCompleted(0)
                .build();

        userRepository.save(admin);
        log.info("Platform admin seeded: {}", adminEmail);
    }
}
