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
    // Gateway health contract
    @Test
    void gatewayHealthReportsDownstreamServices() {
        // given: the common RequestSpecification in Api contains the gateway address and headers
        // when: the health resource is requested
        Response response = Api.request(Api.GATEWAY).when().get("/health");
        // then: body and Content-Type are part of the contract, not only HTTP status
        response.then().statusCode(200).contentType("application/json")
                .body("status", equalTo("ok"), "service", equalTo("gateway"),
                        "version", equalTo("1.0.0"), "services", notNullValue(),
                        "services.switch", equalTo("ok"), "services.authorization", equalTo("ok"),
                        "services.cardManagement", equalTo("ok"), "services.logger", equalTo("ok"));
    }

    // #TC-CM-01, #TC-CM-03, #TC-CM-05, #TC-CM-36
    @Test
    void cardCanBeCreatedReadAndFilteredWithUniqueData() {
        // given: a card fixture creates its own PAN and known balance through the API
        String pan = CardFixture.create();
        // when: the card is read and filtered by its generated PAN
        Response card = Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan);
        Response page = Api.request(Api.GATEWAY).queryParam("limit", 1).queryParam("bin", "400000")
                .when().get("/api/cards");
        // then: all observable card fields and the one-item page are checked
        card.then().statusCode(200).body("pan", equalTo(pan), "bin", equalTo("400000"),
                "status", equalTo("ACTIVE"), "currencyCode", equalTo("643"),
                "id", matchesPattern("[0-9a-f-]{36}"), "createdAt", notNullValue())
                .header("X-Cache", oneOf("MISS", "HIT"));
        page.then().statusCode(200).body("total", greaterThanOrEqualTo(1), "cards", hasSize(1),
                "cards[0].bin", equalTo("400000"));
    }

    // #TC-CM-02, #TC-CM-25
    @ParameterizedTest
    @ValueSource(strings = {"abc", "40000", "4000007"})
    void invalidPanIsRejectedWithFieldMessage(String pan) {
        // given: each value represents one invalid equivalence class for a path parameter
        // when: the card resource is addressed with that value
        Response response = Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan);
        // then: the client error identifies the invalid field
        response.then().statusCode(anyOf(is(400), is(404))).body("message", not(emptyOrNullString()));
    }

    // Card list pagination boundary
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void nonPositiveCardPageLimitIsRejected(int limit) {
        // given: a boundary value from the Positive constraint on limit
        // when: cards are searched with that boundary
        Response response = Api.request(Api.GATEWAY).queryParam("limit", limit).when().get("/api/cards");
        // then: validation is a 4xx response with a useful body
        response.then().statusCode(400).body("message", containsString("limit"));
    }

    // Reserve/rollback idempotency case from lecture 10
    @Test
    void duplicateReservationAndRollbackPreserveContract() {
        // given: an active card with a known balance and one unique RRN
        String pan = CardFixture.create();
        String rrn = Data.digits(12);
        Map<String, Object> reservation = Map.of("amount", 100, "rrn", rrn);
        // when: reserve is repeated, then rollback is sent twice
        Response first = Api.request(Api.GATEWAY).body(reservation).when().post("/api/cards/{pan}/reserve", pan);
        Response duplicate = Api.request(Api.GATEWAY).body(reservation).when().post("/api/cards/{pan}/reserve", pan);
        Response rollback = Api.request(Api.GATEWAY).body(Map.of("amount", 100, "rrn", rrn, "pan", pan))
                .when().post("/api/cards/{pan}/rollback", pan);
        Response rollbackAgain = Api.request(Api.GATEWAY).body(Map.of("amount", 100, "rrn", rrn, "pan", pan))
                .when().post("/api/cards/{pan}/rollback", pan);
        // then: every response body distinguishes success, duplicate, and already rolled-back state
        first.then().statusCode(200).body("pan", equalTo(pan), "availableBalance", equalTo(999900));
        duplicate.then().statusCode(409).body("message", containsString("RRN"));
        rollback.then().statusCode(200).body("availableBalance", equalTo(1000000));
        rollbackAgain.then().statusCode(409).body("message", containsString("already"));
    }
}
