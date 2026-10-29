package com.processing.authorization.services;

import com.processing.authorization.client.BinLookupClient;
import com.processing.authorization.client.CardManagementClient;
import com.processing.authorization.events.AuthServiceAuthorizeEvent;
import com.processing.authorization.events.AuthorizationEventNotifier;
import com.processing.authorization.exceptions.CardNotFoundException;
import com.processing.authorization.exceptions.InsufficientFundsException;
import com.processing.authorization.exceptions.ServiceUnavailableException;
import com.processing.authorization.repositories.LimitUsageRepository;
import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackRequest;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.common.dto.cardmanagement.CardModel;
import com.processing.common.dto.cardmanagement.CardModelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String PAN = "4000001234567893";
    private static final String BIN = "400000";
    private static final String ISSUER_ID = "ISS001";
    private static final BigDecimal AMOUNT = new BigDecimal("10000");
    private static final BigDecimal BALANCE = new BigDecimal("1000000");
    private static final Instant REQUEST_TIME = Instant.now();

    @Mock
    private LimitUsageRepository limitUsageRepository;

    @Mock
    private AuthorizationEventNotifier eventNotifier;

    @Mock
    private BinLookupClient binLookupClient;

    @Mock
    private CardManagementClient cardManagementClient;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                limitUsageRepository,
                eventNotifier,
                binLookupClient,
                cardManagementClient);
    }

    @Test
    void shouldApproveAuthorizationWhenCardIsActiveAndSufficientFunds() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(activeCard());
        when(binLookupClient.getIssuerId(PAN)).thenReturn(Optional.of(ISSUER_ID));
        when(limitUsageRepository.upsertLimitUsage(anyString(), any(), any(), any(), any())).thenReturn(1);
        when(limitUsageRepository.fetchRrnBlock()).thenReturn(1L);

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_APPROVED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_APPROVED);
        assertThat(response.rrn()).isNotBlank();
        assertThat(response.authCode()).isNotBlank();
        verify(cardManagementClient).reserve(AMOUNT, response.rrn(), PAN);
        verify(eventNotifier).notify(any(AuthServiceAuthorizeEvent.class));
    }

    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = CardModelStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"ACTIVE"})
    void shouldDeclineWhenCardStatusIsNotActive(CardModelStatus status) {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(cardWithStatus(status));

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        verify(cardManagementClient, never()).reserve(any(), anyString(), anyString());
        verify(eventNotifier).notify(any());
    }

    // TC-AUTH-02/03/04: не-ACTIVE статусы отклоняются с конкретной причиной
    @ParameterizedTest(name = "status={0} -> reason={1}")
    @CsvSource({
            "INACTIVE, CARD_INACTIVE",
            "BLOCKED, CARD_BLOCKED",
            "EXPIRED, CARD_EXPIRED"
    })
    void shouldDeclineWithSpecificReasonPerStatus(CardModelStatus status, String reason) {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(cardWithStatus(status));

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.declineReason()).isEqualTo(reason);
        verify(cardManagementClient, never()).reserve(any(), anyString(), anyString());
    }

    @Test
    void shouldDeclineWhenCardNotFound() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenThrow(new CardNotFoundException(PAN));

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.declineReason()).isEqualTo("CARD_NOT_FOUND");
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_CARD_NOT_FOUND);
    }

    @Test
    void shouldDeclineWhenCardStatusIsNull() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(cardWithStatus(null));

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.declineReason()).isEqualTo("UNKNOWN_REASON");
    }

    @Test
    void shouldDeclineWhenCardIsExpiredByDate() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(cardWithExpiry(YearMonth.of(2025, 1)));

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.declineReason()).isEqualTo("CARD_EXPIRED");
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_CARD_EXPIRED);
    }

    @Test
    void shouldDeclineWhenInsufficientFunds() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(cardWithBalance(new BigDecimal("500")));

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.declineReason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_INSUFFICIENT_FUNDS);
    }

    @Test
    void shouldDeclineWhenLimitIsExceeded() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(activeCard());
        when(limitUsageRepository.upsertLimitUsage(anyString(), any(), any(), any(), any())).thenReturn(0);

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.declineReason()).isEqualTo("EXCEEDS_AMOUNT_LIMIT");
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_EXCEEDS_LIMIT);
        verify(cardManagementClient, never()).reserve(any(), anyString(), anyString());
    }

    @Test
    void shouldDeclineWhenReserveThrowsInsufficientFunds() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(activeCard());
        when(limitUsageRepository.upsertLimitUsage(anyString(), any(), any(), any(), any())).thenReturn(1);
        when(limitUsageRepository.fetchRrnBlock()).thenReturn(1L);
        doThrow(new InsufficientFundsException(PAN)).when(cardManagementClient).reserve(any(), anyString(), anyString());

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.declineReason()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void shouldDeclineWhenGetCardThrowsServiceUnavailable() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenThrow(new ServiceUnavailableException("cms down"));

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.declineReason()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_SERVICE_UNAVAILABLE);
    }

    // TC-AUTH-05: dailyLimit ON — сумма ровно на границе дневного лимита
    @Test
    void shouldApproveWhenAmountEqualsDailyLimit() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(cardWithDailyLimit(AMOUNT));
        when(limitUsageRepository.upsertLimitUsage(anyString(), any(), any(), any(), any())).thenReturn(1);
        when(limitUsageRepository.fetchRrnBlock()).thenReturn(1L);

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_APPROVED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_APPROVED);
    }

    // TC-AUTH-07: availableBalance ON — сумма ровно на границе баланса
    @Test
    void shouldApproveWhenAmountEqualsAvailableBalance() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(cardWithBalance(AMOUNT));
        when(limitUsageRepository.upsertLimitUsage(anyString(), any(), any(), any(), any())).thenReturn(1);
        when(limitUsageRepository.fetchRrnBlock()).thenReturn(1L);

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_APPROVED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_APPROVED);
    }

    // TC-AUTH-09: expiryDate ON — срок действия = текущий месяц
    @Test
    void shouldApproveWhenCardExpiresInCurrentMonth() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(cardWithExpiry(YearMonth.now()));
        when(limitUsageRepository.upsertLimitUsage(anyString(), any(), any(), any(), any())).thenReturn(1);
        when(limitUsageRepository.fetchRrnBlock()).thenReturn(1L);

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_APPROVED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_APPROVED);
    }

    // TC-AUTH-30: сумма равна дневному лимиту и балансу одновременно
    @Test
    void shouldApproveWhenAmountEqualsDailyLimitAndBalance() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(cardWithLimits(AMOUNT, AMOUNT));
        when(limitUsageRepository.upsertLimitUsage(anyString(), any(), any(), any(), any())).thenReturn(1);
        when(limitUsageRepository.fetchRrnBlock()).thenReturn(1L);

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_APPROVED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_APPROVED);
    }

    // TC-AUTH-34: monthlyLimit ON — сумма ровно на границе месячного лимита
    @Test
    void shouldApproveWhenAmountEqualsMonthlyLimit() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(cardWithMonthlyLimit(AMOUNT));
        when(limitUsageRepository.upsertLimitUsage(anyString(), any(), any(), any(), any())).thenReturn(1);
        when(limitUsageRepository.fetchRrnBlock()).thenReturn(1L);

        // Act
        AuthorizationResponse response = authService.authorize(validRequest(), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_APPROVED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_APPROVED);
    }

    @Test
    void shouldApproveRollbackWhenCardManagementSucceeds() {
        // Arrange
        RollbackRequest request = rollbackRequest();

        // Act
        RollbackResponse response = authService.rollback(request, REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(RollbackResponse.STATUS_APPROVED);
        assertThat(response.responseCode()).isEqualTo(RollbackResponse.CODE_SUCCESS);
        verify(cardManagementClient).rollback(request);
    }

    private AuthorizationRequest validRequest() {
        return new AuthorizationRequest(
                "0100", "000001", PAN, "000000",
                AMOUNT, "643", REQUEST_TIME,
                "TERM0001", "POS", "MERCH0000000002", "5045", "ACQ002", ISSUER_ID);
    }

    private RollbackRequest rollbackRequest() {
        return new RollbackRequest("000000000001", PAN, AMOUNT);
    }

    private CardModel activeCard() {
        return new CardModel(
                UUID.randomUUID(), PAN, BIN, "IVAN IVANOV",
                YearMonth.of(2027, 12), CardModelStatus.ACTIVE, "643",
                new BigDecimal("1000000"), new BigDecimal("2000000"), BALANCE,
                ISSUER_ID, Instant.now());
    }

    private CardModel cardWithStatus(CardModelStatus status) {
        return new CardModel(
                UUID.randomUUID(), PAN, BIN, "IVAN IVANOV",
                YearMonth.of(2027, 12), status, "643",
                new BigDecimal("1000000"), new BigDecimal("2000000"), BALANCE,
                ISSUER_ID, Instant.now());
    }

    private CardModel cardWithExpiry(YearMonth expiry) {
        return new CardModel(
                UUID.randomUUID(), PAN, BIN, "IVAN IVANOV",
                expiry, CardModelStatus.ACTIVE, "643",
                new BigDecimal("1000000"), new BigDecimal("2000000"), BALANCE,
                ISSUER_ID, Instant.now());
    }

    private CardModel cardWithBalance(BigDecimal balance) {
        return new CardModel(
                UUID.randomUUID(), PAN, BIN, "IVAN IVANOV",
                YearMonth.of(2027, 12), CardModelStatus.ACTIVE, "643",
                new BigDecimal("1000000"), new BigDecimal("2000000"), balance,
                ISSUER_ID, Instant.now());
    }

    private CardModel cardWithDailyLimit(BigDecimal dailyLimit) {
        return new CardModel(
                UUID.randomUUID(), PAN, BIN, "IVAN IVANOV",
                YearMonth.of(2027, 12), CardModelStatus.ACTIVE, "643",
                dailyLimit, new BigDecimal("2000000"), BALANCE,
                ISSUER_ID, Instant.now());
    }

    private CardModel cardWithMonthlyLimit(BigDecimal monthlyLimit) {
        return new CardModel(
                UUID.randomUUID(), PAN, BIN, "IVAN IVANOV",
                YearMonth.of(2027, 12), CardModelStatus.ACTIVE, "643",
                new BigDecimal("1000000"), monthlyLimit, BALANCE,
                ISSUER_ID, Instant.now());
    }

    private CardModel cardWithLimits(BigDecimal dailyLimit, BigDecimal balance) {
        return new CardModel(
                UUID.randomUUID(), PAN, BIN, "IVAN IVANOV",
                YearMonth.of(2027, 12), CardModelStatus.ACTIVE, "643",
                dailyLimit, new BigDecimal("2000000"), balance,
                ISSUER_ID, Instant.now());
    }
}