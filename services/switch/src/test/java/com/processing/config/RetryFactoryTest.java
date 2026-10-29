package com.processing.config;

import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetryFactoryTest {

    private static final int MAX_ATTEMPTS = 3;

    @ParameterizedTest(name = "backoff={0} retries to maxAttempts")
    @MethodSource("backoffLists")
    void shouldRetryConfiguredAttempts(List<Long> backoff) throws Exception {
        // Arrange
        Callable<Object> callable = mock(Callable.class);
        when(callable.call()).thenThrow(new RuntimeException("boom"));

        // Act + Assert
        assertThatThrownBy(() -> Retry.decorateCallable(
                RetryFactory.authorizationRetry(properties(backoff)), callable).call())
                .isInstanceOf(RuntimeException.class);

        // Assert
        verify(callable, times(MAX_ATTEMPTS)).call();
    }

    @Test
    void shouldExposeConfiguredMaxAttemptsOnRetry() {
        // Arrange
        Retry retry = RetryFactory.authorizationRetry(properties(List.of(1L)));

        // Act
        int maxAttempts = retry.getRetryConfig().getMaxAttempts();

        // Assert
        assertThat(maxAttempts).isEqualTo(MAX_ATTEMPTS);
    }

    private static Stream<Arguments> backoffLists() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(List.of()),
                Arguments.of(List.of(1L, 2L, 4L)));
    }

    private SwitchProperties properties(List<Long> backoff) {
        return new SwitchProperties(
                "1.0", Map.of(), "http://localhost:8083", "http://localhost:8088",
                "http://localhost:8084",
                new SwitchProperties.HttpProperties(1000, 1000, 1000),
                new SwitchProperties.RetryProperties(MAX_ATTEMPTS, backoff),
                new SwitchProperties.CircuitBreakerSection(
                        new SwitchProperties.CircuitBreakerProperties(50, 10, 5, 10000)));
    }
}