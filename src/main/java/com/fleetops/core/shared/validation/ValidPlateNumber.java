package com.fleetops.core.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PlateNumberValidator.class)
@Documented
public @interface ValidPlateNumber {
    String message() default "Invalid Nigerian plate number format (e.g. KJA-123AB)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
