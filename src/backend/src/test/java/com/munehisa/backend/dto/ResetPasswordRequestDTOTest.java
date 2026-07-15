package com.munehisa.backend.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResetPasswordRequestDTOTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void validRequest_passesValidation() {
        Set<ConstraintViolation<ResetPasswordRequestDTO>> violations =
                validator.validate(new ResetPasswordRequestDTO("some-token", "new-password"));

        assertTrue(violations.isEmpty());
    }

    @Test
    void blankToken_failsValidation() {
        Set<ConstraintViolation<ResetPasswordRequestDTO>> violations =
                validator.validate(new ResetPasswordRequestDTO("", "new-password"));

        assertFalse(violations.isEmpty());
    }

    @Test
    void newPasswordUnder8Characters_failsValidation() {
        Set<ConstraintViolation<ResetPasswordRequestDTO>> violations =
                validator.validate(new ResetPasswordRequestDTO("some-token", "short"));

        assertFalse(violations.isEmpty());
    }

    @Test
    void nullNewPassword_passesValidation() {
        // Known gap: newPassword only has @Size(min = 8), no @NotBlank, so
        // null bypasses Bean Validation entirely and reaches the service layer.
        Set<ConstraintViolation<ResetPasswordRequestDTO>> violations =
                validator.validate(new ResetPasswordRequestDTO("some-token", null));

        assertTrue(violations.isEmpty());
    }
}
