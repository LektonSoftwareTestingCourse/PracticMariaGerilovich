package com.processing.gateway.circuitbreaker;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCircuitBreakerTest {

    private static final String SERVICE = "authorization";

    private final TestClock clock = new TestClock(Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void shouldAllowRequestWhenCircuitIsClosed() {
        // Arrange
        InMemoryCircuitBreaker breaker = breaker(Duration.ofSeconds(10), 3);

        // Act
        boolean allowed = breaker.allowRequest(SERVICE);

        // Assert
        assertThat(allowed).isTrue();
    }

    @Test
    void shouldOpenAfterThresholdFailuresAndRejectRequests() {
        // Arrange
        InMemoryCircuitBreaker breaker = breaker(Duration.ofSeconds(10), 3);

        // Act
        breaker.recordFailure(SERVICE);
        breaker.recordFailure(SERVICE);
        breaker.recordFailure(SERVICE);

        // Assert
        assertThat(breaker.allowRequest(SERVICE)).isFalse();
    }

    @Test
    void shouldAllowHalfOpenProbeAfterOpenDurationElapsed() {
        // Arrange
        InMemoryCircuitBreaker breaker = breaker(Duration.ofSeconds(10), 3);
        openCircuit(breaker);

        // Act
        clock.advance(Duration.ofSeconds(11));

        // Assert
        assertThat(breaker.allowRequest(SERVICE)).isTrue();
    }

    @Test
    void shouldRejectSecondHalfOpenProbeWhileFirstIsInFlight() {
        // Arrange
        InMemoryCircuitBreaker breaker = breaker(Duration.ofSeconds(10), 3);
        openCircuit(breaker);
        clock.advance(Duration.ofSeconds(11));

        // Act
        breaker.allowRequest(SERVICE);

        // Assert
        assertThat(breaker.allowRequest(SERVICE)).isFalse();
    }

    @Test
    void shouldCloseCircuitAfterSuccessInHalfOpen() {
        // Arrange
        InMemoryCircuitBreaker breaker = breaker(Duration.ofSeconds(10), 3);
        openCircuit(breaker);
        clock.advance(Duration.ofSeconds(11));
        breaker.allowRequest(SERVICE);

        // Act
        breaker.recordSuccess(SERVICE);

        // Assert
        assertThat(breaker.allowRequest(SERVICE)).isTrue();
    }

    @Test
    void shouldOpenImmediatelyOnFailureInHalfOpen() {
        // Arrange
        InMemoryCircuitBreaker breaker = breaker(Duration.ofSeconds(10), 3);
        openCircuit(breaker);
        clock.advance(Duration.ofSeconds(11));
        breaker.allowRequest(SERVICE);

        // Act
        breaker.recordFailure(SERVICE);

        // Assert
        assertThat(breaker.allowRequest(SERVICE)).isFalse();
    }

    @Test
    void shouldReleaseHalfOpenProbeSlot() {
        // Arrange
        InMemoryCircuitBreaker breaker = breaker(Duration.ofSeconds(10), 3);
        openCircuit(breaker);
        clock.advance(Duration.ofSeconds(11));
        breaker.allowRequest(SERVICE);

        // Act
        breaker.releaseRequest(SERVICE);

        // Assert
        assertThat(breaker.allowRequest(SERVICE)).isTrue();
    }

    private InMemoryCircuitBreaker breaker(Duration openDuration, int threshold) {
        return InMemoryCircuitBreaker.forTesting(openDuration, threshold, clock);
    }

    private void openCircuit(InMemoryCircuitBreaker breaker) {
        breaker.recordFailure(SERVICE);
        breaker.recordFailure(SERVICE);
        breaker.recordFailure(SERVICE);
    }

    private static final class TestClock extends Clock {
        private Instant instant;

        private TestClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}