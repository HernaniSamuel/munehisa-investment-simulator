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

class ForgotPasswordRequestDTOTest {

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
    void validEmail_passesValidation() {
        Set<ConstraintViolation<ForgotPasswordRequestDTO>> violations =
                validator.validate(new ForgotPasswordRequestDTO("ada@example.com"));

        assertTrue(violations.isEmpty());
    }

    @Test
    void blankEmail_failsValidation() {
        Set<ConstraintViolation<ForgotPasswordRequestDTO>> violations =
                validator.validate(new ForgotPasswordRequestDTO(""));

        assertFalse(violations.isEmpty());
    }

    @Test
    void malformedEmail_failsValidation() {
        Set<ConstraintViolation<ForgotPasswordRequestDTO>> violations =
                validator.validate(new ForgotPasswordRequestDTO("not-an-email"));

        assertFalse(violations.isEmpty());
    }
}
