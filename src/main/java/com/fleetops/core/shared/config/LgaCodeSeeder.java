package com.fleetops.core.shared.config;

import com.fleetops.core.module.lga.model.LgaCode;
import com.fleetops.core.module.lga.repository.LgaCodeRepository;
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

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class LgaCodeSeeder implements ApplicationRunner {

    private final LgaCodeRepository lgaCodeRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (lgaCodeRepository.count() > 0) {
            log.info("LGA codes already seeded — skipping");
            return;
        }

        List<LgaCode> codes = new ArrayList<>();
        var resource = new ClassPathResource("nigeria_plate_codes.csv");
        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; }
                String[] parts = line.split(",");
                if (parts.length >= 3 && !parts[0].isBlank()) {
                    codes.add(LgaCode.builder()
                            .code(parts[0].trim().toUpperCase())
                            .lga(parts[1].trim())
                            .state(parts[2].trim())
                            .build());
                }
            }
        }

        lgaCodeRepository.saveAll(codes);
        log.info("Seeded {} LGA codes", codes.size());
    }
}
