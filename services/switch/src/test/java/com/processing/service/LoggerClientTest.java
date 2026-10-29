package com.processing.service;

import com.processing.common.dto.transactionlogger.TransactionRequest;
import com.processing.common.dto.transactionlogger.TransactionStatus;
import com.processing.config.RabbitMQConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoggerClientTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private LoggerClient loggerClient;

    @BeforeEach
    void setUp() {
        loggerClient = new LoggerClient(rabbitTemplate);
    }

    @Test
    void shouldReturnTrueWhenTransactionPublished() {
        // Arrange
        TransactionRequest transaction = transactionRequest();

        // Act
        boolean published = loggerClient.log(transaction);

        // Assert
        assertThat(published).isTrue();
        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, transaction);
    }

    @Test
    void shouldReturnFalseWhenBrokerRejectsMessage() {
        // Arrange
        TransactionRequest transaction = transactionRequest();
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate)
                .convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, transaction);

        // Act
        boolean published = loggerClient.log(transaction);

        // Assert
        assertThat(published).isFalse();
    }

    private TransactionRequest transactionRequest() {
        return new TransactionRequest(
                UUID.randomUUID(), "0100", "000001", "000000000001",
                "4000001234567893", "000000", new BigDecimal("10000"), "643",
                "TERM0001", "POS", "MERCH0000000002", "5045", "ACQ002", "ISS001",
                new BigDecimal("100"), TransactionStatus.APPROVED, null,
                "A1B2C3", 100, Instant.now(), Instant.now());
    }
}