package com.fleetops.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PlateNumberConstraintValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPlateNumber {

    String message() default "Invalid plate number. Must follow Nigerian format (e.g. KJA-245BX) " +
            "with a recognised LGA prefix and a sequence number between 001 and 999";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
