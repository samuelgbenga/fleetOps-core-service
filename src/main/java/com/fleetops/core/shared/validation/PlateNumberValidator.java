package com.fleetops.core.shared.validation;

import com.fleetops.core.module.lga.repository.LgaCodeRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class PlateNumberValidator implements ConstraintValidator<ValidPlateNumber, String> {

    private static final Pattern PLATE_PATTERN = Pattern.compile("^([A-Z]{3})-([1-9]\\d{2})([A-Z]{2})$");

    private final LgaCodeRepository lgaCodeRepository;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return false;
        String normalised = value.trim().toUpperCase();
        var matcher = PLATE_PATTERN.matcher(normalised);
        if (!matcher.matches()) return false;
        String lgaCode = matcher.group(1);
        return lgaCodeRepository.existsByCode(lgaCode);
    }
}
