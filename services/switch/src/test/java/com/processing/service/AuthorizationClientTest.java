package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.config.SwitchProperties;
import com.processing.exception.AuthorizationException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthorizationClientTest {

    private static final String PAN = "4000001234567893";
    private static final String STAN = "000001";
    private static final String AUTH_URL = "http://localhost:8083";

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec getUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec getHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private SwitchProperties properties;

    @BeforeEach
    void setUp() {
        properties = properties();
    }

    @Test
    void shouldThrowAuthUnavailableWithCauseMessageOnFailure() {
        // Arrange
        doThrow(new RuntimeException(new IllegalStateException("connection refused")))
                .when(restClient).post();
        AuthorizationClient client = client(retry(1), closedCircuitBreaker());

        // Act + Assert
        assertThatThrownBy(() -> client.authorize(request()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("connection refused");
    }

    @Test
    void shouldThrowAuthUnavailableWithOwnMessageWhenNoCause() {
        // Arrange
        doThrow(new RuntimeException("boom")).when(restClient).post();
        AuthorizationClient client = client(retry(1), closedCircuitBreaker());

        // Act + Assert
        assertThatThrownBy(() -> client.authorize(request()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void shouldReturnNullWhenRollbackFails() {
        // Arrange
        doThrow(new RuntimeException("down")).when(restClient).post();
        AuthorizationClient client = client(retry(1), closedCircuitBreaker());

        // Act
        RollbackResponse response = client.rollback(request(), "000000000001");

        // Assert
        assertThat(response).isNull();
    }

    @Test
    void shouldReturnOkWhenAuthorizationHealthy() {
        // Arrange
        when(restClient.get()).thenReturn(getUriSpec);
        when(getUriSpec.uri(anyString())).thenReturn(getHeadersSpec);
        when(getHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());
        AuthorizationClient client = client(retry(1), closedCircuitBreaker());

        // Act
        String health = client.checkHealth();

        // Assert
        assertThat(health).isEqualTo("ok");
    }

    @Test
    void shouldReturnDownWhenHealthCheckFails() {
        // Arrange
        when(restClient.get()).thenReturn(getUriSpec);
        when(getUriSpec.uri(anyString())).thenReturn(getHeadersSpec);
        when(getHeadersSpec.retrieve()).thenReturn(responseSpec);
        doThrow(new RuntimeException("down")).when(responseSpec).toBodilessEntity();
        AuthorizationClient client = client(retry(1), closedCircuitBreaker());

        // Act
        String health = client.checkHealth();

        // Assert
        assertThat(health).isEqualTo("down");
    }

    private AuthorizationClient client(Retry retry, CircuitBreaker circuitBreaker) {
        return new AuthorizationClient(properties, restClient, retry, circuitBreaker);
    }

    private Retry retry(int maxAttempts) {
        return Retry.of("test-retry", RetryConfig.custom().maxAttempts(maxAttempts).build());
    }

    private CircuitBreaker closedCircuitBreaker() {
        return CircuitBreaker.of("test-cb", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .build());
    }

    private AuthorizationRequest request() {
        return new AuthorizationRequest(
                "0100", STAN, PAN, "000000",
                new BigDecimal("10000"), "643", Instant.now(),
                "TERM0001", "POS", "MERCH0000000002", "5045", "ACQ002", "ISS001");
    }

    private SwitchProperties properties() {
        return new SwitchProperties(
                "1.0", Map.of("400000", "ISS001"), AUTH_URL, "http://localhost:8088",
                "http://localhost:8084",
                new SwitchProperties.HttpProperties(1000, 1000, 1000),
                new SwitchProperties.RetryProperties(2, List.of(1L)),
                new SwitchProperties.CircuitBreakerSection(
                        new SwitchProperties.CircuitBreakerProperties(50, 10, 5, 10000)));
    }
}