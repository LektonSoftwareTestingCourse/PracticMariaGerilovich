package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.CardFixture;
import com.processing.api.support.Data;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;

class CardManagementContractApiTest {
    @ParameterizedTest
    @ValueSource(strings = {"40000", "4000007"})
    void invalidBinLengthIsRejected(String bin) {
        Map<String, Object> request = Data.card();
        request.put("bin", bin);
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/cards");
        response.then().statusCode(400).body("message", not(emptyOrNullString()));
    }

    @Test
    void expiryContractIsReadableAndExpiredStateIsReturned() {
        String pan = CardFixture.create();
        Response current = Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan);
        CardFixture.patch(pan, Map.of("status", "EXPIRED"));
        Response expired = Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan);
        current.then().statusCode(200).body("expiryDate", matchesPattern("[0-9]{4}-[0-9]{2}"));
        expired.then().statusCode(200).body("status", equalTo("EXPIRED"),
                "expiryDate", matchesPattern("[0-9]{4}-[0-9]{2}"));
    }

    @Test
    void generatorReturnsTheDocumentedStatusDistribution() {
        Response response = Api.request(Api.GATEWAY)
                .body(Map.of("count", 100, "bins", java.util.List.of("400000")))
                .when().post("/api/cards/generate");
        response.then().statusCode(201).body("generated", equalTo(100), "cards", hasSize(100),
                "cards.findAll { it.status == 'ACTIVE' }.size()", equalTo(95),
                "cards.findAll { it.status == 'INACTIVE' }.size()", equalTo(3),
                "cards.findAll { it.status == 'BLOCKED' }.size()", equalTo(2),
                "cards[0].pan", matchesPattern("[0-9]{16}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "INACTIVE", "BLOCKED", "EXPIRED"})
    void cardsCanBeFilteredByEveryContractStatus(String status) {
        String pan = CardFixture.create();
        if (!"ACTIVE".equals(status)) {
            CardFixture.patch(pan, Map.of("status", status));
        }
        Response response = Api.request(Api.GATEWAY).queryParam("status", status).queryParam("limit", 100)
                .when().get("/api/cards");
        response.then().statusCode(200).body("cards", notNullValue(),
                "cards.findAll { it.status != '%s' }.size()".formatted(status), equalTo(0));
    }

    @Test
    void blockedCardCanBeReadAndPatchedToInactive() {
        String pan = CardFixture.create();
        CardFixture.patch(pan, Map.of("status", "BLOCKED"));
        Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan).then()
                .statusCode(200).body("status", equalTo("BLOCKED"));
        CardFixture.patch(pan, Map.of("status", "INACTIVE"));
        Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan).then()
                .statusCode(200).body("status", equalTo("INACTIVE"));
    }

    @Test
    void missingCardIsReportedByReadPatchAndDelete() {
        String pan = "4000001234560001";
        Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan).then().statusCode(404);
        Api.request(Api.GATEWAY).body(Map.of("status", "ACTIVE"))
                .when().patch("/api/cards/{pan}", pan).then().statusCode(404);
        Api.request(Api.GATEWAY).when().delete("/api/cards/{pan}", pan).then().statusCode(404);
    }

    @Test
    void invalidPanIsRejectedByReadPatchAndDelete() {
        String pan = "123";
        Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan).then()
                .statusCode(anyOf(is(400), is(404)));
        Api.request(Api.GATEWAY).body(Map.of("status", "ACTIVE"))
                .when().patch("/api/cards/{pan}", pan).then().statusCode(anyOf(is(400), is(404)));
        Api.request(Api.GATEWAY).when().delete("/api/cards/{pan}", pan).then()
                .statusCode(anyOf(is(400), is(404)));
    }

    @Test
    void reserveOnMissingCardReturnsNotFound() {
        String pan = "4000001234560001";
        Map<String, Object> request = Map.of("amount", 100, "rrn", Data.digits(12));
        Response response = Api.request(Api.GATEWAY).body(request).when()
                .post("/api/cards/{pan}/reserve", pan);
        response.then().statusCode(404).body("message", not(emptyOrNullString()));
    }

    @Test
    void reserveWithInvalidPanIsRejected() {
        Response response = Api.request(Api.GATEWAY).body(Map.of("amount", 100, "rrn", Data.digits(12)))
                .when().post("/api/cards/{pan}/reserve", "bad-pan");
        response.then().statusCode(anyOf(is(400), is(404))).body("message", not(emptyOrNullString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BLOCKED", "EXPIRED"})
    void reserveFromNonActiveCardIsRejected(String status) {
        String pan = CardFixture.create();
        CardFixture.patch(pan, Map.of("status", status));
        Response response = Api.request(Api.GATEWAY)
                .body(Map.of("amount", 100, "rrn", Data.digits(12)))
                .when().post("/api/cards/{pan}/reserve", pan);
        response.then().statusCode(409).body("message", containsString("status"));
    }

    @Test
    void reserveWithInsufficientFundsReturnsPaymentRequired() {
        Map<String, Object> card = new LinkedHashMap<>(Data.card());
        card.put("initialBalance", 1);
        String pan = CardFixture.create(card);
        Response response = Api.request(Api.GATEWAY)
                .body(Map.of("amount", 100, "rrn", Data.digits(12)))
                .when().post("/api/cards/{pan}/reserve", pan);
        response.then().statusCode(402).body("message", containsString("funds"));
    }

    @Test
    void createWithUnknownBinIsRejected() {
        Map<String, Object> request = new LinkedHashMap<>(Data.card());
        request.put("bin", "999999");
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/cards");
        response.then().statusCode(anyOf(is(400), is(404))).body("message", not(emptyOrNullString()));
    }

    @Test
    void lowercaseCardholderNameIsRejected() {
        Map<String, Object> request = new LinkedHashMap<>(Data.card());
        request.put("cardholderName", "Maria Gerilovich");
        Api.request(Api.GATEWAY).body(request).when().post("/api/cards").then()
                .statusCode(400).body("message", not(emptyOrNullString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"64", "6430", "RUB"})
    void invalidCurrencyCodeIsRejected(String currencyCode) {
        Map<String, Object> request = new LinkedHashMap<>(Data.card());
        request.put("currencyCode", currencyCode);
        Api.request(Api.GATEWAY).body(request).when().post("/api/cards").then()
                .statusCode(400).body("message", not(emptyOrNullString()));
    }

    @Test
    void missingCardholderNameIsRejected() {
        Map<String, Object> request = new LinkedHashMap<>(Data.card());
        request.remove("cardholderName");
        Api.request(Api.GATEWAY).body(request).when().post("/api/cards").then()
                .statusCode(400).body("message", not(emptyOrNullString()));
    }

    @Test
    void missingBinIsRejected() {
        Map<String, Object> request = new LinkedHashMap<>(Data.card());
        request.remove("bin");
        Api.request(Api.GATEWAY).body(request).when().post("/api/cards").then()
                .statusCode(400).body("message", not(emptyOrNullString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dailyLimit", "monthlyLimit"})
    void negativeDailyOrMonthlyLimitIsRejected(String field) {
        Map<String, Object> request = new LinkedHashMap<>(Data.card());
        request.put(field, -1);
        Api.request(Api.GATEWAY).body(request).when().post("/api/cards").then()
                .statusCode(400).body("message", not(emptyOrNullString()));
    }
}
