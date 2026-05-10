package com.fleetops.core.config;

import com.fleetops.core.vehicle.entity.LgaCode;
import com.fleetops.core.vehicle.repository.LgaCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class LgaCodeSeeder implements ApplicationRunner {

    private final LgaCodeRepository lgaCodeRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (lgaCodeRepository.count() > 0) {
            log.info("LGA codes already seeded — skipping.");
            return;
        }

        ClassPathResource resource = new ClassPathResource("nigeria_plate_codes.csv");
        List<LgaCode> codes = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream()))) {

            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; } // skip header
                String[] parts = line.split(",", 3);
                if (parts.length < 3) continue;

                codes.add(LgaCode.builder()
                        .code(parts[0].trim().toUpperCase())
                        .lga(parts[1].trim())
                        .state(parts[2].trim())
                        .build());
            }
        }

        lgaCodeRepository.saveAll(codes);
        log.info("Seeded {} LGA plate codes.", codes.size());
    }
}
