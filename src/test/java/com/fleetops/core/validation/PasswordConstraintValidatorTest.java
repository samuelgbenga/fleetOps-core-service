package com.fleetops.core.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordConstraintValidatorTest {

    private PasswordConstraintValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordConstraintValidator();
    }

    // ── valid passwords ──────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "Admin@1234",
            "Staff@5678",
            "Secure!9x",
            "P@ssw0rd",
            "Fleet$99Manager",
            "Maint@1234"
    })
    void validPasswords_returnTrue(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    // ── invalid passwords ────────────────────────────────────────────────────

    @Test
    void null_returnsFalse() {
        assertThat(validator.isValid(null, null)).isFalse();
    }

    @Test
    void blank_returnsFalse() {
        assertThat(validator.isValid("   ", null)).isFalse();
    }

    @Test
    void tooShort_returnsFalse() {
        assertThat(validator.isValid("Ab1@xyz", null)).isFalse();
    }

    @Test
    void noUppercase_returnsFalse() {
        assertThat(validator.isValid("admin@1234", null)).isFalse();
    }

    @Test
    void noLowercase_returnsFalse() {
        assertThat(validator.isValid("ADMIN@1234", null)).isFalse();
    }

    @Test
    void noDigit_returnsFalse() {
        assertThat(validator.isValid("Admin@abcd", null)).isFalse();
    }

    @Test
    void noSpecialChar_returnsFalse() {
        assertThat(validator.isValid("Admin12345", null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "12345678", "PASSWORD!", "abcdefgh"})
    void commonWeakPasswords_returnFalse(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }
}
