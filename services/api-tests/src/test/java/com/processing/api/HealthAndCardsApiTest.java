package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.CardFixture;
import com.processing.api.support.Data;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.hamcrest.Matchers.*;

class HealthAndCardsApiTest {
    @Test
    void gatewayHealthReportsDownstreamServices() {
        Response response = Api.request(Api.GATEWAY).when().get("/health");
        response.then().statusCode(200).contentType("application/json")
                .body("status", equalTo("ok"), "service", equalTo("gateway"),
                        "version", equalTo("1.0.0"), "services", notNullValue(),
                        "services.switch", equalTo("ok"), "services.authorization", equalTo("ok"),
                        "services.cardManagement", equalTo("ok"), "services.logger", equalTo("ok"));
    }

    @Test
    void gatewayHealthContainsStableServiceMetadata() {
        Api.request(Api.GATEWAY).when().get("/health").then().statusCode(200)
                .body("service", equalTo("gateway"), "version", matchesPattern("\\d+\\.\\d+\\.\\d+"),
                        "services", allOf(notNullValue(), aMapWithSize(4)));
    }

    @Test
    void cardCanBeCreatedReadAndFilteredWithUniqueData() {
        String pan = CardFixture.create();
        Response card = Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan);
        Response page = Api.request(Api.GATEWAY).queryParam("limit", 1).queryParam("bin", "400000")
                .when().get("/api/cards");
        card.then().statusCode(200).body("pan", equalTo(pan), "bin", equalTo("400000"),
                "status", equalTo("ACTIVE"), "currencyCode", equalTo("643"),
                "id", matchesPattern("[0-9a-f-]{36}"), "createdAt", notNullValue())
                .header("X-Cache", oneOf("MISS", "HIT"));
        page.then().statusCode(200).body("total", greaterThanOrEqualTo(1), "cards", hasSize(1),
                "cards[0].bin", equalTo("400000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "40000", "4000007"})
    void invalidPanIsRejectedWithFieldMessage(String pan) {
        Response response = Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan);
        response.then().statusCode(anyOf(is(400), is(404))).body("message", not(emptyOrNullString()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void nonPositiveCardPageLimitIsRejected(int limit) {
        Response response = Api.request(Api.GATEWAY).queryParam("limit", limit).when().get("/api/cards");
        response.then().statusCode(400).body("message", containsString("limit"));
    }

    @Test
    void duplicateReservationAndRollbackPreserveContract() {
        String pan = CardFixture.create();
        String rrn = Data.digits(12);
        Map<String, Object> reservation = Map.of("amount", 100, "rrn", rrn);
        Response first = Api.request(Api.GATEWAY).body(reservation).when().post("/api/cards/{pan}/reserve", pan);
        Response duplicate = Api.request(Api.GATEWAY).body(reservation).when().post("/api/cards/{pan}/reserve", pan);
        Response rollback = Api.request(Api.GATEWAY).body(Map.of("amount", 100, "rrn", rrn, "pan", pan))
                .when().post("/api/cards/{pan}/rollback", pan);
        Response rollbackAgain = Api.request(Api.GATEWAY).body(Map.of("amount", 100, "rrn", rrn, "pan", pan))
                .when().post("/api/cards/{pan}/rollback", pan);
        first.then().statusCode(200).body("pan", equalTo(pan), "availableBalance", equalTo(999900));
        duplicate.then().statusCode(409).body("message", containsString("RRN"));
        rollback.then().statusCode(200).body("availableBalance", equalTo(1000000));
        rollbackAgain.then().statusCode(409).body("message", containsString("already"));
    }
}
