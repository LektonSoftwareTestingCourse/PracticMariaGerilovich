package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationRequestNormalizerTest {

    @Test
    void shouldKeepTerminalIdWhenNull() {
        assertThat(AuthorizationRequestNormalizer.normalizeTerminalId(null)).isNull();
    }

    @Test
    void shouldKeepTerminalIdWhenBlank() {
        assertThat(AuthorizationRequestNormalizer.normalizeTerminalId("   ")).isEqualTo("   ");
    }

    @Test
    void shouldTruncateTerminalIdWhenLongerThanEight() {
        assertThat(AuthorizationRequestNormalizer.normalizeTerminalId("TERM000123")).isEqualTo("TERM0001");
    }

    @Test
    void shouldPadTerminalIdWithZerosWhenShorterThanEight() {
        assertThat(AuthorizationRequestNormalizer.normalizeTerminalId("TERM0")).isEqualTo("TERM0000");
    }

    @Test
    void shouldKeepTerminalIdWhenExactlyEight() {
        assertThat(AuthorizationRequestNormalizer.normalizeTerminalId("TERM0001")).isEqualTo("TERM0001");
    }

    @Test
    void shouldKeepMerchantIdWhenNull() {
        assertThat(AuthorizationRequestNormalizer.normalizeMerchantId(null)).isNull();
    }

    @Test
    void shouldKeepMerchantIdWhenBlank() {
        assertThat(AuthorizationRequestNormalizer.normalizeMerchantId("   ")).isEqualTo("   ");
    }

    @Test
    void shouldKeepMerchantIdWhenExactlyFifteen() {
        assertThat(AuthorizationRequestNormalizer.normalizeMerchantId("MERCH0000000002")).isEqualTo("MERCH0000000002");
    }

    @Test
    void shouldRemoveLeadingZeroAfterMerchPrefix() {
        assertThat(AuthorizationRequestNormalizer.normalizeMerchantId("MERCH00000000002"))
                .isEqualTo("MERCH0000000002");
    }

    @Test
    void shouldTruncateMerchantIdWhenLongerThanFifteen() {
        assertThat(AuthorizationRequestNormalizer.normalizeMerchantId("MERCH000000000012345"))
                .isEqualTo("MERCH0000000000");
    }

    @Test
    void shouldPadMerchantIdWithZerosWhenShorterThanFifteen() {
        assertThat(AuthorizationRequestNormalizer.normalizeMerchantId("MERCH")).isEqualTo("MERCH0000000000");
    }

    @Test
    void shouldTruncateSixteenCharMerchantIdWhenExtraCharIsNotZero() {
        // length 16, starts with MERCH, but 6th char is not '0' → falls to truncate
        assertThat(AuthorizationRequestNormalizer.normalizeMerchantId("MERCH12345678901"))
                .isEqualTo("MERCH1234567890");
    }

    @Test
    void shouldTruncateSixteenCharMerchantIdWithoutMerchPrefix() {
        // length 16 but does not start with MERCH → falls to truncate
        assertThat(AuthorizationRequestNormalizer.normalizeMerchantId("1234567890123456"))
                .isEqualTo("123456789012345");
    }

    @Test
    void shouldNormalizeRequestFields() {
        // Arrange
        AuthorizationRequest request = new AuthorizationRequest(
                "0100", "000001", "4000001234567893", "000000",
                new BigDecimal("10000"), "643", Instant.now(),
                "TERM0", "POS", "MERCH00000000002", "5045", "ACQ002", "ISS001");

        // Act
        AuthorizationRequest normalized = AuthorizationRequestNormalizer.normalize(request);

        // Assert
        assertThat(normalized.terminalId()).isEqualTo("TERM0000");
        assertThat(normalized.merchantId()).isEqualTo("MERCH0000000002");
    }
}
