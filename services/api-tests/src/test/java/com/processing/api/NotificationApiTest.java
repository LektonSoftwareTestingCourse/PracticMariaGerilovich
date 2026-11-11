package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.CardFixture;
import com.processing.api.support.Data;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.hamcrest.Matchers.*;

class NotificationApiTest {
    // #TC-N-08
    @Test
    void cardEventIsDeliveredToNotificationService() {
        // given: a card and a unique reservation identifier created through Card Management API
        String pan = CardFixture.create();
        String rrn = Data.digits(12);
        // when: a card operation creates an outbox event
        Api.request(Api.GATEWAY).body(Map.of("amount", 100, "rrn", rrn))
                .when().post("/api/cards/{pan}/reserve", pan).then().statusCode(200);
        // then: Notification Service eventually exposes the consumed card event
        Awaitility.await("card event reaches Notification Service")
                .atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(250)).untilAsserted(() ->
                        Api.request(Api.NOTIFICATION).queryParam("size", 100).when().get("/api/notifications")
                                .then().statusCode(200)
                                .body("content.findAll { it.routingKey == 'card.CardServiceReserveEvent' "
                                                + "&& it.payload.contains('" + rrn + "') }.size()",
                                        greaterThanOrEqualTo(1)));
    }
}
