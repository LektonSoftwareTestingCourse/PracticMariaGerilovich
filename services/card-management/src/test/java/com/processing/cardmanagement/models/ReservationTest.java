package com.processing.cardmanagement.models;

import com.processing.cardmanagement.exceptions.RollbackAlreadySatisfiedException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    private static final String PAN = "4000001234567893";
    private static final String RRN = "000000000001";

    @Test
    void shouldStartRollbackForReservedReservation() {
        // Arrange
        Reservation reservation = new Reservation(PAN, new BigDecimal("1000"), RRN);

        // Act
        ReservationRollback rollback = reservation.startRollback(new BigDecimal("1000"));

        // Assert
        assertThat(rollback.reservationId()).isEqualTo(reservation.id());
        assertThat(rollback.pan()).isEqualTo(PAN);
        assertThat(rollback.rrn()).isEqualTo(RRN);
        assertThat(rollback.rollbackAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void shouldThrowWhenRollbackAlreadySatisfied() {
        // Arrange
        Reservation reservation = rolledBackReservation();

        // Act + Assert
        assertThatThrownBy(() -> reservation.startRollback(new BigDecimal("1000")))
                .isInstanceOf(RollbackAlreadySatisfiedException.class);
    }

    @Test
    void shouldMarkReservationAsRolledBack() {
        // Arrange
        Reservation reservation = new Reservation(PAN, new BigDecimal("1000"), RRN);
        ReservationRollback rollback = reservation.startRollback(new BigDecimal("1000"));

        // Act
        Reservation updated = reservation.rolledBack(rollback);

        // Assert
        assertThat(updated.status()).isEqualTo(ReservationStatus.ROLLED_BACK);
    }

    @Test
    void shouldThrowWhenRollbackReservationIdDiffers() {
        // Arrange
        Reservation reservation = new Reservation(PAN, new BigDecimal("1000"), RRN);
        ReservationRollback foreign = new ReservationRollback(
                UUID.randomUUID(), UUID.randomUUID(), PAN, new BigDecimal("1000"), RRN, Instant.now());

        // Act + Assert
        assertThatThrownBy(() -> reservation.rolledBack(foreign))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Reservation rolledBackReservation() {
        return new Reservation(
                UUID.randomUUID(), PAN, new BigDecimal("1000"), RRN,
                ReservationStatus.ROLLED_BACK, Instant.now(), Instant.now());
    }
}