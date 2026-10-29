package com.processing.gateway.validation;

import com.processing.common.dto.authorization.AuthorizationRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionRequestValidatorTest {

    private final TransactionRequestValidator validator = new TransactionRequestValidator();

    @Test
    void shouldPassForValidRequest() {
        // Act + Assert
        assertThatCode(() -> validator.validate(validRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldThrowWhenRequestIsNull() {
        // Act + Assert
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("Request body");
    }

    @Test
    void shouldThrowWhenMtiIsNot0100() {
        // Act + Assert
        assertThatThrownBy(() -> validator.validate(withMti(validRequest(), "0200")))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("mti");
    }

    @Test
    void shouldThrowWhenPanIsNot16Digits() {
        // Act + Assert
        assertThatThrownBy(() -> validator.validate(withPan(validRequest(), "1234")))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("pan");
    }

    @Test
    void shouldThrowWhenAmountIsNull() {
        // Act + Assert
        assertThatThrownBy(() -> validator.validate(withAmount(validRequest(), null)))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void shouldThrowWhenAmountIsNotPositive() {
        // Act + Assert
        assertThatThrownBy(() -> validator.validate(withAmount(validRequest(), BigDecimal.ZERO)))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void shouldThrowWhenCurrencyCodeIsNot3Characters() {
        // Act + Assert
        assertThatThrownBy(() -> validator.validate(withCurrency(validRequest(), "64")))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("currencyCode");
    }

    @Test
    void shouldThrowWhenMccIsNot4Digits() {
        // Act + Assert
        assertThatThrownBy(() -> validator.validate(withMcc(validRequest(), "50")))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("mcc");
    }

    @Test
    void shouldThrowWhenStanIsMissing() {
        // Act + Assert
        assertThatThrownBy(() -> validator.validate(withStan(validRequest(), null)))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessageContaining("stan");
    }

    private AuthorizationRequest validRequest() {
        return new AuthorizationRequest(
                "0100", "000001", "4000001234567893", "000000",
                new BigDecimal("10000"), "643", Instant.now(),
                "TERM0001", "POS", "MERCH0000000002", "5045", "ACQ002", "ISS001");
    }

    private AuthorizationRequest withMti(AuthorizationRequest request, String mti) {
        return new AuthorizationRequest(
                mti, request.stan(), request.pan(), request.processingCode(),
                request.amount(), request.currencyCode(), request.transmissionDateTime(),
                request.terminalId(), request.terminalType(), request.merchantId(),
                request.mcc(), request.acquirerId(), request.issuerId());
    }

    private AuthorizationRequest withPan(AuthorizationRequest request, String pan) {
        return new AuthorizationRequest(
                request.mti(), request.stan(), pan, request.processingCode(),
                request.amount(), request.currencyCode(), request.transmissionDateTime(),
                request.terminalId(), request.terminalType(), request.merchantId(),
                request.mcc(), request.acquirerId(), request.issuerId());
    }

    private AuthorizationRequest withAmount(AuthorizationRequest request, BigDecimal amount) {
        return new AuthorizationRequest(
                request.mti(), request.stan(), request.pan(), request.processingCode(),
                amount, request.currencyCode(), request.transmissionDateTime(),
                request.terminalId(), request.terminalType(), request.merchantId(),
                request.mcc(), request.acquirerId(), request.issuerId());
    }

    private AuthorizationRequest withCurrency(AuthorizationRequest request, String currencyCode) {
        return new AuthorizationRequest(
                request.mti(), request.stan(), request.pan(), request.processingCode(),
                request.amount(), currencyCode, request.transmissionDateTime(),
                request.terminalId(), request.terminalType(), request.merchantId(),
                request.mcc(), request.acquirerId(), request.issuerId());
    }

    private AuthorizationRequest withMcc(AuthorizationRequest request, String mcc) {
        return new AuthorizationRequest(
                request.mti(), request.stan(), request.pan(), request.processingCode(),
                request.amount(), request.currencyCode(), request.transmissionDateTime(),
                request.terminalId(), request.terminalType(), request.merchantId(),
                mcc, request.acquirerId(), request.issuerId());
    }

    private AuthorizationRequest withStan(AuthorizationRequest request, String stan) {
        return new AuthorizationRequest(
                request.mti(), stan, request.pan(), request.processingCode(),
                request.amount(), request.currencyCode(), request.transmissionDateTime(),
                request.terminalId(), request.terminalType(), request.merchantId(),
                request.mcc(), request.acquirerId(), request.issuerId());
    }
}