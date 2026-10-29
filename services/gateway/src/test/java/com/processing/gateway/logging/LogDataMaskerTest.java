package com.processing.gateway.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogDataMaskerTest {

    private final LogDataMasker masker = new LogDataMasker();

    @Test
    void shouldMaskPan() {
        // Act
        String masked = masker.maskData("{\"pan\":\"4000001234567893\",\"amount\":100}");

        // Assert
        assertThat(masked).contains("\"pan\":\"400000****7893\"");
        assertThat(masked).doesNotContain("4000001234567893");
    }

    @Test
    void shouldMaskCvv() {
        // Act
        String masked = masker.maskData("{\"cvv\":\"123\"}");

        // Assert
        assertThat(masked).contains("\"cvv\":\"***\"");
    }

    @Test
    void shouldReturnDataUnchangedWhenNoSensitiveFields() {
        // Act
        String masked = masker.maskData("{\"amount\":100}");

        // Assert
        assertThat(masked).isEqualTo("{\"amount\":100}");
    }
}
