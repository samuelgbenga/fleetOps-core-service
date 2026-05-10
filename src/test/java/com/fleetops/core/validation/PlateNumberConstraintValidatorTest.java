package com.fleetops.core.validation;

import com.fleetops.core.vehicle.repository.LgaCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlateNumberConstraintValidatorTest {

    @Mock private LgaCodeRepository lgaCodeRepository;
    @InjectMocks private PlateNumberConstraintValidator validator;

    @BeforeEach
    void stubValidPrefix() {
        when(lgaCodeRepository.existsByCode("KJA")).thenReturn(true);
        when(lgaCodeRepository.existsByCode("PHC")).thenReturn(true);
        when(lgaCodeRepository.existsByCode("ABJ")).thenReturn(true);
    }

    // ── valid inputs ─────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"KJA-245BX", "PHC-001AA", "ABJ-999ZZ"})
    void validPlates_uppercase_returnTrue(String plate) {
        assertThat(validator.isValid(plate, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"kja-245bx", "Kja-245Bx", " KJA-245BX "})
    void validPlates_lowercaseOrPaddedWithSpaces_returnTrue(String plate) {
        assertThat(validator.isValid(plate, null)).isTrue();
    }

    // ── format failures ──────────────────────────────────────────────────────

    @Test
    void null_returnsFalse() {
        assertThat(validator.isValid(null, null)).isFalse();
    }

    @Test
    void blank_returnsFalse() {
        assertThat(validator.isValid("   ", null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "KJ-245BX",       // prefix too short
            "KJAA-245BX",     // prefix too long
            "KJA245BX",       // missing hyphen
            "KJA-24BX",       // only 2 digits
            "KJA-2456BX",     // 4 digits
            "KJA-245B",       // only 1 suffix letter
            "KJA-245BXY",     // 3 suffix letters
            "KJA-245bx",      // lowercase suffix after normalisation still fails if repo returns false
            "123-245BX",      // digits in prefix
    })
    void malformedFormat_returnsFalse(String plate) {
        when(lgaCodeRepository.existsByCode(anyString())).thenReturn(false);
        assertThat(validator.isValid(plate, null)).isFalse();
    }

    @Test
    void sequenceNumber000_returnsFalse() {
        assertThat(validator.isValid("KJA-000BX", null)).isFalse();
    }

    // ── invalid LGA prefix ───────────────────────────────────────────────────

    @Test
    void unknownPrefix_returnsFalse() {
        when(lgaCodeRepository.existsByCode("ZZZ")).thenReturn(false);
        assertThat(validator.isValid("ZZZ-245BX", null)).isFalse();
    }

    @Test
    void knownPrefix_validFormat_returnsTrue() {
        assertThat(validator.isValid("KJA-245BX", null)).isTrue();
    }
}
