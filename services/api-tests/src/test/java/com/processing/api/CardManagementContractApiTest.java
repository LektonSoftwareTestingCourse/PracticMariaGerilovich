package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.CardFixture;
import com.processing.api.support.Data;
import io.restassured.response.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/** Cases from docs/practice-3-pyramid/test-cases.md, TC-CM-01..38. */
class CardManagementContractApiTest {
    // #TC-CM-04, #TC-CM-05, #TC-CM-06
    @ParameterizedTest
    @ValueSource(strings = {"40000", "4000007"})
    void invalidBinLengthIsRejected(String bin) {
        // given: a valid create request with exactly one invalid field
        Map<String, Object> request = Data.card();
        request.put("bin", bin);
        // when: card creation is requested
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/cards");
        // then: BIN validation is reported as a client error with a body
        response.then().statusCode(400).body("message", not(emptyOrNullString()));
    }

    // #TC-CM-07, #TC-CM-08, #TC-CM-35
    @Test
    @Tag("fault")
    void expiryContractIsReadableAndExpiredStateIsReturned() {
        // given: a newly created card has a four-digit expiry representation in the contract
        String pan = CardFixture.create();
        // when: it is read, then changed to the expired state through PATCH
        Response current = Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan);
        CardFixture.patch(pan, Map.of("status", "EXPIRED"));
        Response expired = Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan);
        // then: current value has the documented shape and state transition is observable
        current.then().statusCode(200).body("expiryDate", matchesPattern("[0-9]{4}"));
        expired.then().statusCode(200).body("status", equalTo("EXPIRED"),
                "expiryDate", matchesPattern("[0-9]{4}"));
    }

    // #TC-CM-13, #TC-CM-23, #TC-CM-24
    @Test
    void generatorReturnsTheDocumentedStatusDistribution() {
        // given: 100 generated cards makes the 95/3/2 percentages deterministic in the implementation
        // when: the generator receives one known BIN
        Response response = Api.request(Api.GATEWAY)
                .body(Map.of("count", 100, "bins", java.util.List.of("400000")))
                .when().post("/api/cards/generate");
        // then: count, PAN format and each status class are checked
        response.then().statusCode(201).body("generated", equalTo(100), "cards", hasSize(100),
                "cards.findAll { it.status == 'ACTIVE' }.size()", equalTo(95),
                "cards.findAll { it.status == 'INACTIVE' }.size()", equalTo(3),
                "cards.findAll { it.status == 'BLOCKED' }.size()", equalTo(2),
                "cards[0].pan", matchesPattern("[0-9]{16}"));
    }

    // #TC-CM-14, #TC-CM-18, #TC-CM-20, #TC-CM-22
    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "INACTIVE", "BLOCKED", "EXPIRED"})
    void cardsCanBeFilteredByEveryContractStatus(String status) {
        // given: this test creates a card and sets its requested state through the API
        String pan = CardFixture.create();
        if (!"ACTIVE".equals(status)) {
            CardFixture.patch(pan, Map.of("status", status));
        }
        // when: the status filter is applied
        Response response = Api.request(Api.GATEWAY).queryParam("status", status).queryParam("limit", 100)
                .when().get("/api/cards");
        // then: every returned record belongs to the requested equivalence class
        response.then().statusCode(200).body("cards", notNullValue(),
                "cards.findAll { it.status != '%s' }.size()".formatted(status), equalTo(0));
    }

    // #TC-CM-16, #TC-CM-17
    @Test
    void blockedCardCanBeReadAndPatchedToInactive() {
        // given: a card is placed in BLOCKED state by its public API
        String pan = CardFixture.create();
        CardFixture.patch(pan, Map.of("status", "BLOCKED"));
        // when: it is read and then changed to INACTIVE
        Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan).then()
                .statusCode(200).body("status", equalTo("BLOCKED"));
        CardFixture.patch(pan, Map.of("status", "INACTIVE"));
        // then: the state change is returned by the read contract
        Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan).then()
                .statusCode(200).body("status", equalTo("INACTIVE"));
    }

    // #TC-CM-15, #TC-CM-19, #TC-CM-26, #TC-CM-34
    @Test
    void missingCardIsReportedByReadPatchAndDelete() {
        // given: a valid PAN-shaped value that was not created by this test
        String pan = "4000001234560001";
        // when: read, patch and delete address the missing resource
        Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan).then().statusCode(404);
        Api.request(Api.GATEWAY).body(Map.of("status", "ACTIVE"))
                .when().patch("/api/cards/{pan}", pan).then().statusCode(404);
        Api.request(Api.GATEWAY).when().delete("/api/cards/{pan}", pan).then().statusCode(404);
    }

    // #TC-CM-12, #TC-CM-21, #TC-CM-25, #TC-CM-28
    @Test
    void invalidPanIsRejectedByReadPatchAndDelete() {
        // given: PAN fails the shared @Pan constraint
        String pan = "123";
        // when: all card operations receive that invalid path value
        Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan).then()
                .statusCode(anyOf(is(400), is(404)));
        Api.request(Api.GATEWAY).body(Map.of("status", "ACTIVE"))
                .when().patch("/api/cards/{pan}", pan).then().statusCode(anyOf(is(400), is(404)));
        Api.request(Api.GATEWAY).when().delete("/api/cards/{pan}", pan).then()
                .statusCode(anyOf(is(400), is(404)));
    }

    // #TC-CM-10, #TC-CM-30, #TC-CM-33
    @Test
    void reserveOnMissingCardReturnsNotFound() {
        // given: a valid PAN shape and a unique RRN without a corresponding card
        String pan = "4000001234560001";
        Map<String, Object> request = Map.of("amount", 100, "rrn", Data.digits(12));
        // when: funds are reserved
        Response response = Api.request(Api.GATEWAY).body(request).when()
                .post("/api/cards/{pan}/reserve", pan);
        // then: resource absence is distinct from insufficient funds
        response.then().statusCode(404).body("message", not(emptyOrNullString()));
    }

    // #TC-CM-11, #TC-CM-29
    @Test
    void reserveWithInvalidPanIsRejected() {
        // given: one invalid path value and otherwise valid reserve data
        // when: reserve is called
        Response response = Api.request(Api.GATEWAY).body(Map.of("amount", 100, "rrn", Data.digits(12)))
                .when().post("/api/cards/{pan}/reserve", "bad-pan");
        // then: path validation returns a 4xx response
        response.then().statusCode(anyOf(is(400), is(404))).body("message", not(emptyOrNullString()));
    }

    // #TC-CM-27, #TC-CM-37, #TC-CM-38
    @ParameterizedTest
    @ValueSource(strings = {"BLOCKED", "EXPIRED"})
    void reserveFromNonActiveCardIsRejected(String status) {
        // given: card state is prepared via PATCH, with a valid unique RRN
        String pan = CardFixture.create();
        CardFixture.patch(pan, Map.of("status", status));
        // when: reserve is attempted
        Response response = Api.request(Api.GATEWAY)
                .body(Map.of("amount", 100, "rrn", Data.digits(12)))
                .when().post("/api/cards/{pan}/reserve", pan);
        // then: the card state prevents a reservation
        response.then().statusCode(409).body("message", containsString("status"));
    }

    // #TC-CM-32
    @Test
    void reserveWithInsufficientFundsReturnsPaymentRequired() {
        // given: explicit initial balance is lower than the requested amount
        Map<String, Object> card = new LinkedHashMap<>(Data.card());
        card.put("initialBalance", 1);
        String pan = CardFixture.create(card);
        // when: reserve exceeds available balance
        Response response = Api.request(Api.GATEWAY)
                .body(Map.of("amount", 100, "rrn", Data.digits(12)))
                .when().post("/api/cards/{pan}/reserve", pan);
        // then: the card service uses HTTP 402 for insufficient funds
        response.then().statusCode(402).body("message", containsString("funds"));
    }

    // #TC-CM-31
    @Test
    void createWithUnknownBinIsRejected() {
        // given: BIN is well-formed but absent from the issuer mapping
        Map<String, Object> request = new LinkedHashMap<>(Data.card());
        request.put("bin", "999999");
        // when: card creation is requested
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/cards");
        // then: unknown BIN is a domain error, not a successful card
        response.then().statusCode(anyOf(is(400), is(404))).body("message", not(emptyOrNullString()));
    }
}
