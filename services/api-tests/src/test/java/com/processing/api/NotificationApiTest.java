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
        String pan = CardFixture.create();
        String rrn = Data.digits(12);
        Api.request(Api.GATEWAY).body(Map.of("amount", 100, "rrn", rrn))
                .when().post("/api/cards/{pan}/reserve", pan).then().statusCode(200);
        Awaitility.await("card event reaches Notification Service")
                .atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(250)).untilAsserted(() ->
                        Api.request(Api.NOTIFICATION).queryParam("size", 100).when().get("/api/notifications")
                                .then().statusCode(200)
                                .body("content.findAll { it.routingKey == 'card.CardServiceReserveEvent' "
                                                + "&& it.payload.contains('" + rrn + "') }.size()",
                                        greaterThanOrEqualTo(1)));
    }

    @Test
    void notificationEndpointReturnsPageMetadata() {
        Api.request(Api.NOTIFICATION).queryParam("page", 0).queryParam("size", 1)
                .when().get("/api/notifications").then().statusCode(200)
                .body("content", notNullValue(), "pageable", notNullValue(),
                        "totalElements", greaterThanOrEqualTo(0));
    }
}
