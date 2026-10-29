package com.processing.gateway.shutdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GracefulShutdownStateTest {

    private final GracefulShutdownState state = new GracefulShutdownState();

    @Test
    void shouldNotBeShuttingDownInitially() {
        assertThat(state.isShuttingDown()).isFalse();
    }

    @Test
    void shouldBeShuttingDownAfterStart() {
        state.startShutdown();
        assertThat(state.isShuttingDown()).isTrue();
    }
}
