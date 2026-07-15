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

class RegisterRequestDTOTest {

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
    void validRegistration_passesValidation() {
        Set<ConstraintViolation<RegisterRequestDTO>> violations = validator.validate(
                new RegisterRequestDTO("Ada Lovelace", "ada@example.com", "correct-password"));

        assertTrue(violations.isEmpty());
    }

    @Test
    void blankName_failsValidation() {
        Set<ConstraintViolation<RegisterRequestDTO>> violations = validator.validate(
                new RegisterRequestDTO("", "ada@example.com", "correct-password"));

        assertFalse(violations.isEmpty());
    }

    @Test
    void blankEmail_failsValidation() {
        Set<ConstraintViolation<RegisterRequestDTO>> violations = validator.validate(
                new RegisterRequestDTO("Ada Lovelace", "", "correct-password"));

        assertFalse(violations.isEmpty());
    }

    @Test
    void malformedEmail_failsValidation() {
        Set<ConstraintViolation<RegisterRequestDTO>> violations = validator.validate(
                new RegisterRequestDTO("Ada Lovelace", "not-an-email", "correct-password"));

        assertFalse(violations.isEmpty());
    }

    @Test
    void passwordUnder8Characters_failsValidation() {
        Set<ConstraintViolation<RegisterRequestDTO>> violations = validator.validate(
                new RegisterRequestDTO("Ada Lovelace", "ada@example.com", "short"));

        assertFalse(violations.isEmpty());
    }

    @Test
    void nullPassword_failsValidation() {
        Set<ConstraintViolation<RegisterRequestDTO>> violations = validator.validate(
                new RegisterRequestDTO("Ada Lovelace", "ada@example.com", null));

        assertFalse(violations.isEmpty());
    }
}
