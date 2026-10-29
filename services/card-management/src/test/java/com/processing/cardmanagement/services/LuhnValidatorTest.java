package com.processing.cardmanagement.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LuhnValidatorTest {

    private final LuhnValidator validator = new LuhnValidator();

    @Test
    void shouldReturnTrueForValidPan() {
        assertThat(validator.isValid("79927398713")).isTrue();
    }

    @Test
    void shouldReturnFalseForInvalidPan() {
        assertThat(validator.isValid("79927398710")).isFalse();
    }

    @Test
    void shouldGenerateValidPan() {
        // Act
        String pan = validator.generatePan("400000");

        // Assert
        assertThat(validator.isValid(pan)).isTrue();
        assertThat(pan).startsWith("400000");
        assertThat(pan).hasSize(16);
    }
}
