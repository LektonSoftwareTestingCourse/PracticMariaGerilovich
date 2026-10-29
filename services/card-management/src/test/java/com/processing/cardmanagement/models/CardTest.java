package com.processing.cardmanagement.models;

import com.processing.cardmanagement.exceptions.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardTest {

    private static final String PAN = "4000001234567893";
    private static final String BIN = "400000";
    private static final BigDecimal BALANCE = new BigDecimal("1000");
    private static final BigDecimal DAILY_LIMIT = new BigDecimal("5000");
    private static final BigDecimal MONTHLY_LIMIT = new BigDecimal("100000");

    @Test
    void shouldStartReservationForActiveCardWithSufficientFunds() {
        // Arrange
        Card card = activeCard();

        // Act
        Reservation reservation = card.startReservation(new BigDecimal("100"), "000000000001");

        // Assert
        assertThat(reservation.pan()).isEqualTo(PAN);
        assertThat(reservation.reservationAmount()).isEqualByComparingTo("100");
        assertThat(reservation.status()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void shouldThrowWhenReservingOnNonActiveCard() {
        // Arrange
        Card card = cardWithStatus(CardStatus.BLOCKED);

        // Act + Assert
        assertThatThrownBy(() -> card.startReservation(new BigDecimal("100"), "000000000001"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowWhenFundsAreInsufficient() {
        // Arrange
        Card card = activeCard();

        // Act + Assert
        assertThatThrownBy(() -> card.startReservation(new BigDecimal("2000"), "000000000001"))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void shouldSubtractReservedAmountFromBalance() {
        // Arrange
        Card card = activeCard();
        Reservation reservation = new Reservation(PAN, new BigDecimal("200"), "000000000001");

        // Act
        Card updated = card.withReservation(reservation);

        // Assert
        assertThat(updated.availableBalance()).isEqualByComparingTo("800");
    }

    @Test
    void shouldThrowWhenReservationPanDiffers() {
        // Arrange
        Card card = activeCard();
        Reservation reservation = new Reservation("9999990000000000", new BigDecimal("200"), "000000000001");

        // Act + Assert
        assertThatThrownBy(() -> card.withReservation(reservation))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAddRollbackAmountToBalance() {
        // Arrange
        Card card = activeCard();
        ReservationRollback rollback = new ReservationRollback(
                UUID.randomUUID(), UUID.randomUUID(), PAN, new BigDecimal("150"), "000000000001", null);

        // Act
        Card updated = card.withRollback(rollback);

        // Assert
        assertThat(updated.availableBalance()).isEqualByComparingTo("1150");
    }

    @Test
    void shouldMarkCardAsDeleted() {
        // Arrange
        Card card = activeCard();

        // Act
        Card deleted = card.deleted();

        // Assert
        assertThat(deleted.status()).isEqualTo(CardStatus.DELETED);
    }

    @Test
    void shouldThrowWhenDeletingAlreadyDeletedCard() {
        // Arrange
        Card card = cardWithStatus(CardStatus.DELETED);

        // Act + Assert
        assertThatThrownBy(card::deleted).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldUpdateCardData() {
        // Arrange
        Card card = activeCard();

        // Act
        Card updated = card.withData(CardStatus.BLOCKED, DAILY_LIMIT, MONTHLY_LIMIT, BALANCE);

        // Assert
        assertThat(updated.status()).isEqualTo(CardStatus.BLOCKED);
        assertThat(updated.dailyLimit()).isEqualTo(DAILY_LIMIT);
    }

    @Test
    void shouldThrowWhenLimitIsNegative() {
        // Arrange
        Card card = activeCard();

        // Act + Assert
        assertThatThrownBy(() -> card.withData(CardStatus.ACTIVE,
                new BigDecimal("-1"), MONTHLY_LIMIT, BALANCE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenDailyLimitExceedsMonthlyLimit() {
        // Arrange
        Card card = activeCard();

        // Act + Assert
        assertThatThrownBy(() -> card.withData(CardStatus.ACTIVE,
                new BigDecimal("100000"), new BigDecimal("5000"), BALANCE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldBuildCardFromDraft() {
        // Arrange
        CardDraft draft = new CardDraft(
                BIN, "IVAN IVANOV", CardStatus.ACTIVE, "643",
                DAILY_LIMIT, MONTHLY_LIMIT, BALANCE);

        // Act
        Card card = Card.fromDraft(PAN, "ISS001", 5, draft);

        // Assert
        assertThat(card.pan()).isEqualTo(PAN);
        assertThat(card.bin()).isEqualTo(BIN);
        assertThat(card.issuerId()).isEqualTo("ISS001");
        assertThat(card.status()).isEqualTo(CardStatus.ACTIVE);
        assertThat(card.expiryDate()).isEqualTo(YearMonth.now().plusYears(5));
    }

    @Test
    void shouldAllowReservationOnActiveCard() {
        // Arrange
        Card card = activeCard();

        // Act + Assert
        card.checkStatusOrThrow();
    }

    @Test
    void shouldThrowOnReservationCheckWhenNotActive() {
        // Arrange
        Card card = cardWithStatus(CardStatus.EXPIRED);

        // Act + Assert
        assertThatThrownBy(card::checkStatusOrThrow).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldCopyCardWithNewPan() {
        // Arrange
        Card card = activeCard();

        // Act
        Card copy = card.copyWithPan("4000000000001111");

        // Assert
        assertThat(copy.pan()).isEqualTo("4000000000001111");
        assertThat(copy.bin()).isEqualTo(BIN);
    }

    private Card activeCard() {
        return cardWithStatus(CardStatus.ACTIVE);
    }

    private Card cardWithStatus(CardStatus status) {
        return new Card(
                UUID.randomUUID(), PAN, BIN, "IVAN IVANOV",
                YearMonth.of(2027, 12), status, "643",
                DAILY_LIMIT, MONTHLY_LIMIT, BALANCE, "ISS001");
    }
}