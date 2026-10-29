package com.processing.service;

import com.processing.config.SwitchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MerchantAcquirerClientTest {

    private static final String PAN = "4000001234567893";

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private MerchantAcquirerClient client;

    @BeforeEach
    void setUp() {
        SwitchProperties properties = new SwitchProperties(
                "1.0", Map.of(), "http://localhost:8083", "http://localhost:8088",
                "http://localhost:8084",
                new SwitchProperties.HttpProperties(1000, 1000, 1000),
                new SwitchProperties.RetryProperties(2, List.of(1L)),
                new SwitchProperties.CircuitBreakerSection(
                        new SwitchProperties.CircuitBreakerProperties(50, 10, 5, 10000)));
        client = new MerchantAcquirerClient(properties, restClient);
    }

    @Test
    void shouldReturnNullWhenResponseBodyIsNull() {
        // Arrange
        stubPostChain();

        // Act
        BigDecimal fee = client.fetchAcquiringFee(Instant.now(), "000001", PAN, "TERM0001", new BigDecimal("10000"));

        // Assert
        assertThat(fee).isNull();
    }

    @Test
    void shouldReturnNullWhenMerchantAcquirerFails() {
        // Arrange
        doThrow(new RuntimeException("down")).when(restClient).post();

        // Act
        BigDecimal fee = client.fetchAcquiringFee(Instant.now(), "000001", PAN, "TERM0001", new BigDecimal("10000"));

        // Assert
        assertThat(fee).isNull();
    }

    private void stubPostChain() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenAnswer(invocation -> requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }
}
