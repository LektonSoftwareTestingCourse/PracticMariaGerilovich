package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.exception.AuthorizationException;
import com.processing.exception.UnknownBinException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    private static final String PAN = "4000001234567893";
    private static final String STAN = "000001";
    private static final String ISSUER_ID = "ISS001";
    private static final BigDecimal AMOUNT = new BigDecimal("10000");

    @Mock
    private RoutingService routingService;

    @Mock
    private AuthorizationClient authorizationClient;

    @Mock
    private AcquiringFeeClient acquiringFeeClient;

    @Mock
    private LoggerClient loggerClient;

    private RouteService routeService;

    @BeforeEach
    void setUp() {
        routeService = new RouteService(
                routingService,
                authorizationClient,
                acquiringFeeClient,
                loggerClient);
    }

    @Test
    void shouldRouteApprovedAndLogTransaction() {
        // Arrange
        AuthorizationResponse approved = approvedResponse(100);
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any()))
                .thenReturn(approved);
        when(acquiringFeeClient.fetchAcquiringFee(
                any(), eq(STAN),
                eq(PAN), any(),
                eq(AMOUNT)))
                .thenReturn(new BigDecimal("100"));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_APPROVED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_APPROVED);
        verify(loggerClient).log(any());
    }

    @Test
    void shouldRouteDeclinedAndLogTransaction() {
        // Arrange
        AuthorizationResponse declined = declinedResponse();
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any())).thenReturn(declined);
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        verify(loggerClient).log(any());
    }

    @Test
    void shouldRollbackAndReturnSystemErrorWhenApprovedButLogFails() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any()))
                .thenReturn(approvedResponse(100));
        when(loggerClient.log(any())).thenReturn(false);
        when(authorizationClient.rollback(any(),
                eq("000000000001")))
                .thenReturn(rollbackApproved());

        // Act
        AuthorizationResponse response = routeService.route(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_SERVICE_UNAVAILABLE);
        verify(authorizationClient).rollback(any(), eq("000000000001"));
    }

    @Test
    void shouldReturnSystemErrorWhenLogFailsAndRollbackDeclined() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any()))
                .thenReturn(approvedResponse(100));
        when(loggerClient.log(any())).thenReturn(false);
        when(authorizationClient.rollback(any(),
                eq("000000000001")))
                .thenReturn(rollbackDeclined());

        // Act
        AuthorizationResponse response = routeService.route(validRequest());

        // Assert
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldReturnUnknownBinResponseWhenBinNotRouted() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenThrow(new UnknownBinException("400000"));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_CARD_NOT_FOUND);
        verify(loggerClient).log(any());
    }

    @Test
    void shouldReturnAuthUnavailableWhenAuthorizationFails() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any()))
                .thenThrow(new AuthorizationException(STAN, 3, "timeout"));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_DECLINED_GENERAL);
    }

    @Test
    void shouldHandleResponseWithoutProcessingTime() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any()))
                .thenReturn(approvedResponse(null));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_APPROVED);
    }

    @Test
    void shouldRouteRequestWithShortPanToUnknownBin() {
        // Arrange
        String shortPan = "1234";
        when(routingService.getIssuerIdByPan(shortPan)).thenThrow(new UnknownBinException("null-or-short"));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(requestWithPan(shortPan));

        // Assert
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_CARD_NOT_FOUND);
        verify(loggerClient).log(any());
    }

    @Test
    void shouldHandleNullPanAsUnknownBin() {
        // Arrange
        when(routingService.getIssuerIdByPan(null)).thenThrow(new UnknownBinException("null-or-short"));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(requestWithPan(null));

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_CARD_NOT_FOUND);
    }

    @Test
    void shouldNotRollbackWhenLogFailsButResponseIsDeclined() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any())).thenReturn(declinedResponse());
        when(loggerClient.log(any())).thenReturn(false);

        // Act
        AuthorizationResponse response = routeService.route(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        verify(authorizationClient, never()).rollback(any(),
                anyString());
    }

    @Test
    void shouldReturnSystemErrorWhenRollbackReturnsNull() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(any())).thenReturn(approvedResponse(100));
        when(loggerClient.log(any())).thenReturn(false);
        when(authorizationClient.rollback(any(),
                anyString())).thenReturn(null);

        // Act
        AuthorizationResponse response = routeService.route(validRequest());

        // Assert
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_SERVICE_UNAVAILABLE);
    }

    private AuthorizationRequest validRequest() {
        return requestWithPan(PAN);
    }

    private AuthorizationRequest requestWithPan(String pan) {
        return new AuthorizationRequest(
                "0100", STAN, pan, "000000",
                AMOUNT, "643", Instant.now(),
                "TERM0001", "POS", "MERCH0000000002", "5045", "ACQ002", "ISS000");
    }

    private AuthorizationResponse approvedResponse(Integer processingTimeMs) {
        return new AuthorizationResponse(
                "0100", STAN, "000000000001", "A1B2C3",
                AuthorizationResponse.CODE_APPROVED, AuthorizationResponse.STATUS_APPROVED,
                null, processingTimeMs);
    }

    private AuthorizationResponse declinedResponse() {
        return new AuthorizationResponse(
                "0100", STAN, null, null,
                AuthorizationResponse.CODE_DECLINED_GENERAL, AuthorizationResponse.STATUS_DECLINED,
                "CARD_BLOCKED", 50);
    }

    private RollbackResponse rollbackApproved() {
        return new RollbackResponse(
                "000000000001", RollbackResponse.CODE_SUCCESS,
                RollbackResponse.STATUS_APPROVED, null, 10);
    }

    private RollbackResponse rollbackDeclined() {
        return new RollbackResponse(
                "000000000001", RollbackResponse.CODE_DECLINED_GENERAL,
                RollbackResponse.STATUS_DECLINED, "REASON", 10);
    }
}
