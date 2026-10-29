package com.processing.cardmanagement.services;

import com.processing.cardmanagement.events.CardEventNotifier;
import com.processing.cardmanagement.exceptions.CardGenerationLimitException;
import com.processing.cardmanagement.options.CardGeneratorOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardGeneratorServiceTest {

    @Mock
    private CardService cardService;

    @Mock
    private CardEventNotifier eventNotifier;

    private CardGeneratorOptions options;

    private CardGeneratorService generator;

    @BeforeEach
    void setUp() {
        options = new CardGeneratorOptions(
                new BigDecimal("100"),
                new BigDecimal("1000"),
                new BigDecimal("100"),
                new BigDecimal("1000"),
                "643",
                100);
        generator = new CardGeneratorService(cardService, options, eventNotifier);
    }

    @Test
    void shouldThrowWhenCountExceedsMax() {
        // Act + Assert
        assertThatThrownBy(() -> generator.generate(101, List.of("400000")))
                .isInstanceOf(CardGenerationLimitException.class);
    }

    @Test
    void shouldGenerateAndSaveCards() {
        // Arrange
        when(cardService.createCards(anyList())).thenReturn(List.of());

        // Act
        generator.generate(5, List.of("400000"));

        // Assert
        verify(cardService).createCards(anyList());
        verify(eventNotifier).notifyListeners(any());
    }
}
