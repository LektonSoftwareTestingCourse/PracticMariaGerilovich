package com.processing.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationExceptionTest {

    @Test
    void shouldUseUnknownWhenLastErrorIsNull() {
        // Act
        AuthorizationException exception = new AuthorizationException("000001", 3, null);

        // Assert
        assertThat(exception.getMessage()).contains("Last error: unknown");
        assertThat(exception.getStan()).isEqualTo("000001");
    }

    @Test
    void shouldIncludeLastErrorWhenPresent() {
        // Act
        AuthorizationException exception = new AuthorizationException("000001", 3, "timeout");

        // Assert
        assertThat(exception.getMessage()).contains("Last error: timeout");
        assertThat(exception.getStan()).isEqualTo("000001");
    }
}
