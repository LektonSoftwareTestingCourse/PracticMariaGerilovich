package com.processing.cardmanagement.services;

import com.processing.cardmanagement.exceptions.BinAlreadyExistException;
import com.processing.cardmanagement.exceptions.BinNotFoundException;
import com.processing.cardmanagement.models.BinIssuer;
import com.processing.cardmanagement.repositories.BinIssuerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BinIssuerServiceImplTest {

    private static final String BIN = "400000";
    private static final String ISSUER_ID = "ISS001";

    @Mock
    private BinIssuerRepository repository;

    private BinIssuerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BinIssuerServiceImpl(repository);
    }

    @Test
    void shouldReturnIssuerIdWhenBinExists() {
        // Arrange
        when(repository.findByBin(BIN)).thenReturn(Optional.of(new BinIssuer(BIN, ISSUER_ID)));

        // Act
        String issuerId = service.getIssuerId(BIN);

        // Assert
        assertThat(issuerId).isEqualTo(ISSUER_ID);
    }

    @Test
    void shouldThrowWhenBinNotFound() {
        // Arrange
        when(repository.findByBin(BIN)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.getIssuerId(BIN))
                .isInstanceOf(BinNotFoundException.class);
    }

    @Test
    void shouldReturnAllIssuers() {
        // Arrange
        when(repository.findAll()).thenReturn(List.of(new BinIssuer(BIN, ISSUER_ID)));

        // Act
        List<BinIssuer> issuers = service.getAll();

        // Assert
        assertThat(issuers).hasSize(1);
    }

    @Test
    void shouldCreateIssuerWhenBinNotExists() {
        // Arrange
        when(repository.existsByBin(BIN)).thenReturn(false);
        when(repository.save(any(BinIssuer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        BinIssuer created = service.create(BIN, ISSUER_ID);

        // Assert
        assertThat(created.bin()).isEqualTo(BIN);
        assertThat(created.issuerId()).isEqualTo(ISSUER_ID);
        verify(repository).save(new BinIssuer(BIN, ISSUER_ID));
    }

    @Test
    void shouldThrowWhenCreatingExistingBin() {
        // Arrange
        when(repository.existsByBin(BIN)).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> service.create(BIN, ISSUER_ID))
                .isInstanceOf(BinAlreadyExistException.class);
    }

    @Test
    void shouldDeleteIssuerWhenBinExists() {
        // Arrange
        when(repository.existsByBin(BIN)).thenReturn(true);

        // Act
        service.delete(BIN);

        // Assert
        verify(repository).deleteByBin(BIN);
    }

    @Test
    void shouldThrowWhenDeletingUnknownBin() {
        // Arrange
        when(repository.existsByBin(BIN)).thenReturn(false);

        // Act + Assert
        assertThatThrownBy(() -> service.delete(BIN))
                .isInstanceOf(BinNotFoundException.class);
    }
}
