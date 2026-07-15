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

class LoginRequestDTOTest {

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
    void validLogin_passesValidation() {
        Set<ConstraintViolation<LoginRequestDTO>> violations =
                validator.validate(new LoginRequestDTO("ada@example.com", "correct-password"));

        assertTrue(violations.isEmpty());
    }

    @Test
    void blankEmail_failsValidation() {
        Set<ConstraintViolation<LoginRequestDTO>> violations =
                validator.validate(new LoginRequestDTO("", "correct-password"));

        assertFalse(violations.isEmpty());
    }

    @Test
    void malformedEmail_failsValidation() {
        Set<ConstraintViolation<LoginRequestDTO>> violations =
                validator.validate(new LoginRequestDTO("not-an-email", "correct-password"));

        assertFalse(violations.isEmpty());
    }

    @Test
    void passwordUnder8Characters_failsValidation() {
        Set<ConstraintViolation<LoginRequestDTO>> violations =
                validator.validate(new LoginRequestDTO("ada@example.com", "short"));

        assertFalse(violations.isEmpty());
    }

    @Test
    void nullPassword_passesValidation() {
        // Known gap: password only has @Size(min = 8), no @NotBlank, so null
        // bypasses Bean Validation entirely and reaches the service layer.
        Set<ConstraintViolation<LoginRequestDTO>> violations =
                validator.validate(new LoginRequestDTO("ada@example.com", null));

        assertTrue(violations.isEmpty());
    }
}
