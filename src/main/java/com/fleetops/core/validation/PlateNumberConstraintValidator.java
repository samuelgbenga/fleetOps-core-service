package com.fleetops.core.validation;

import com.fleetops.core.vehicle.repository.LgaCodeRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class PlateNumberConstraintValidator implements ConstraintValidator<ValidPlateNumber, String> {

    // ABC-123DE — 3 uppercase, hyphen, 3 digits, 2 uppercase
    private static final Pattern FORMAT = Pattern.compile("^[A-Z]{3}-\\d{3}[A-Z]{2}$");

    private final LgaCodeRepository lgaCodeRepository;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }

        // step 1 & 2: trim + uppercase
        String normalised = value.trim().toUpperCase();

        // step 3: format check
        if (!FORMAT.matcher(normalised).matches()) {
            return false;
        }

        // sequence number must be 001–999 (not 000)
        int sequence = Integer.parseInt(normalised.substring(4, 7));
        if (sequence < 1) {
            return false;
        }

        // step 4: validate prefix against seeded lga_codes table
        String prefix = normalised.substring(0, 3);
        return lgaCodeRepository.existsByCode(prefix);
    }
}
