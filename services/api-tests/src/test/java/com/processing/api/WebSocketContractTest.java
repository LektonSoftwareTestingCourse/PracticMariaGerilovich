package com.processing.api;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class WebSocketContractTest {
    @Test
    @Tag("fault")
    void transactionStreamAcceptsWebSocketHandshake() {
        // The OpenAPI contract declares WS /ws/transactions; run in strict-contract profile.
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            CompletableFuture<WebSocket> connection = HttpClient.newHttpClient().newWebSocketBuilder()
                    .buildAsync(URI.create(System.getProperty("api.ws.url", "ws://localhost:8080/ws/transactions")),
                            new WebSocket.Listener() { });
            WebSocket socket = connection.join();
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "contract test").join();
        });
    }
}
