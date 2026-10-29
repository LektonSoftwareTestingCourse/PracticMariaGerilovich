package com.processing.gateway.downstream;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DownstreamExceptionUtilsTest {

    @Test
    void shouldDetectDownstreamUnavailableForConnectException() {
        assertThat(DownstreamExceptionUtils.isDownstreamUnavailable(new ConnectException("down"))).isTrue();
    }

    @Test
    void shouldDetectDownstreamUnavailableForSocketTimeout() {
        assertThat(DownstreamExceptionUtils.isDownstreamUnavailable(new SocketTimeoutException("timeout"))).isTrue();
    }

    @Test
    void shouldDetectDownstreamUnavailableForResourceAccess() {
        assertThat(DownstreamExceptionUtils.isDownstreamUnavailable(new ResourceAccessException("no route"))).isTrue();
    }

    @Test
    void shouldNotDetectUnavailableForOtherException() {
        assertThat(DownstreamExceptionUtils.isDownstreamUnavailable(new IllegalArgumentException("bad"))).isFalse();
    }

    @Test
    void shouldDetectDownstreamUnavailableFromCauseChain() {
        Exception cause = new ConnectException("down");
        Exception wrapper = new RuntimeException(cause);
        assertThat(DownstreamExceptionUtils.isDownstreamUnavailable(wrapper)).isTrue();
    }

    @Test
    void shouldRethrowIoExceptionAsIs() {
        assertThatThrownBy(() -> DownstreamExceptionUtils.rethrow(new IOException("io")))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldRethrowRuntimeExceptionAsIs() {
        assertThatThrownBy(() -> DownstreamExceptionUtils.rethrow(new IllegalStateException("runtime")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldWrapCheckedExceptionIntoIoException() throws Exception {
        assertThatThrownBy(() -> DownstreamExceptionUtils.rethrow(new Exception("checked")))
                .isInstanceOf(IOException.class);
    }
}
