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

/**
 * No Spring context here: UpdateNameRequestDTO's @NotBlank/@Size are plain
 * Bean Validation, enforced by Spring MVC via @Valid before the request ever
 * reaches UserService - so the "validation failure" scenario is exercised
 * against the DTO itself, not the service.
 */
class UpdateNameRequestDTOTest {

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
    void blankName_failsValidation() {
        Set<ConstraintViolation<UpdateNameRequestDTO>> violations =
                validator.validate(new UpdateNameRequestDTO(""));

        assertFalse(violations.isEmpty());
    }

    @Test
    void nameOver255Characters_failsValidation() {
        String tooLong = "a".repeat(256);

        Set<ConstraintViolation<UpdateNameRequestDTO>> violations =
                validator.validate(new UpdateNameRequestDTO(tooLong));

        assertFalse(violations.isEmpty());
    }

    @Test
    void validName_passesValidation() {
        Set<ConstraintViolation<UpdateNameRequestDTO>> violations =
                validator.validate(new UpdateNameRequestDTO("New Name"));

        assertTrue(violations.isEmpty());
    }
}
