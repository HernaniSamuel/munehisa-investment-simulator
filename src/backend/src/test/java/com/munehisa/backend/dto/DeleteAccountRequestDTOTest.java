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

class DeleteAccountRequestDTOTest {

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
    void validPassword_passesValidation() {
        Set<ConstraintViolation<DeleteAccountRequestDTO>> violations =
                validator.validate(new DeleteAccountRequestDTO("correct-password"));

        assertTrue(violations.isEmpty());
    }

    @Test
    void blankPassword_failsValidation() {
        Set<ConstraintViolation<DeleteAccountRequestDTO>> violations =
                validator.validate(new DeleteAccountRequestDTO(""));

        assertFalse(violations.isEmpty());
    }

    @Test
    void passwordUnder8Characters_failsValidation() {
        Set<ConstraintViolation<DeleteAccountRequestDTO>> violations =
                validator.validate(new DeleteAccountRequestDTO("short"));

        assertFalse(violations.isEmpty());
    }
}
