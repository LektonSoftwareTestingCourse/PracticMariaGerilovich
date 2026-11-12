package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.CardFixture;
import com.processing.api.support.Checks;
import com.processing.api.support.Data;
import io.restassured.response.Response;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.time.Duration;
import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;

class AuthorizationApiTest {
    // #TC-N-11
    @Test
    void approvedTransactionHasGeneratedValuesAndNullDeclineReason() {
        String pan = CardFixture.create();
        Map<String, Object> request = Data.authorization(pan);
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/transactions");
        Checks.approved(response, (String) request.get("stan"));
        response.then().header("Content-Type", containsString("application/json"));
    }

    // #TC-N-07, #TC-N-11
    @Test
    void gatewayTransactionIsEventuallySearchableAndReservesBalance() {
        String pan = CardFixture.create();
        Map<String, Object> request = Data.authorization(pan);
        String stan = (String) request.get("stan");
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/transactions");
        Checks.approved(response, stan);
        Awaitility.await("transaction reaches Logger")
                .atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(250)).untilAsserted(() ->
                        Api.request(Api.GATEWAY).queryParam("pan", pan).queryParam("limit", 50)
                                .when().get("/api/transactions/search").then().statusCode(200)
                                .body("transactions.findAll { it.stan == '%s' }.size()".formatted(stan), equalTo(1)));
        Api.request(Api.CARDS).when().get("/api/cards/{pan}", pan).then()
                .statusCode(200).body("availableBalance", equalTo(999900));
    }

    // #TC-N-04, #TC-N-12 and request validation boundary cases
    @ParameterizedTest
    @CsvSource({"mti,010", "currencyCode,64"})
    void oneMalformedFieldIsRejectedWithValidationBody(String field, String value) {
        String pan = CardFixture.create();
        Map<String, Object> request = Data.authorization(pan);
        request.put(field, value);
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/transactions");
        response.then().statusCode(400).body("message", containsString(field));
    }

    @Test
    void unknownCardIsADeclineWithCardNotFoundReason() {
        Map<String, Object> request = Data.authorization("400000" + Data.digits(10));
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/transactions");
        Checks.declined(response, 200, "14", "CARD_NOT_FOUND");
    }

    @Test
    void insufficientFundsIsDifferentFromUnknownCard() {
        Map<String, Object> card = Data.card();
        card.put("initialBalance", 1);
        String pan = CardFixture.create(card);
        Map<String, Object> request = Data.authorization(pan);
        request.put("amount", 100);
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/transactions");
        Checks.declined(response, 200, "51", "INSUFFICIENT_FUNDS");
    }

    // #TC-N-12
    @Test
    void blockedCardIsADeclineAndLeavesBalanceUntouched() {
        String pan = CardFixture.create();
        CardFixture.patch(pan, Map.of("status", "BLOCKED"));
        Map<String, Object> request = Data.authorization(pan);
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/transactions");
        Checks.declined(response, 200, "05", "CARD_BLOCKED");
        Awaitility.await("blocked card has no reservation side effect")
                .atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(100)).untilAsserted(() ->
                        Api.request(Api.CARDS).when().get("/api/cards/{pan}", pan).then()
                                .statusCode(200).body("availableBalance", equalTo(1000000)));
    }

    @Test
    void expiredCardIsADeclineWithExpirationReason() {
        String pan = CardFixture.create();
        CardFixture.patch(pan, Map.of("status", "EXPIRED"));
        Map<String, Object> request = Data.authorization(pan);
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/transactions");
        Checks.declined(response, 200, "54", "CARD_EXPIRED");
    }

    @Test
    void inactiveCardIsADeclineWithInactiveReason() {
        String pan = CardFixture.create();
        CardFixture.patch(pan, Map.of("status", "INACTIVE"));
        Response response = Api.request(Api.GATEWAY).body(Data.authorization(pan))
                .when().post("/api/transactions");
        Checks.declined(response, 200, "05", "CARD_INACTIVE");
    }

    @Test
    void activeCardWithExpiredTransactionDateIsDeclined() {
        String pan = CardFixture.create();
        Map<String, Object> request = Data.authorization(pan);
        request.put("transmissionDateTime", "2099-01-01T00:00:00Z");
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/transactions");
        Checks.declined(response, 200, "54", "CARD_EXPIRED");
    }

    @Test
    void dailyLimitIsCheckedAcrossTwoTransactions() {
        Map<String, Object> card = Data.card();
        card.put("dailyLimit", 1000);
        String pan = CardFixture.create(card);
        Map<String, Object> firstRequest = Data.authorization(pan);
        firstRequest.put("amount", new BigDecimal("600"));
        Map<String, Object> secondRequest = Data.authorization(pan);
        secondRequest.put("amount", new BigDecimal("600"));
        Api.request(Api.GATEWAY).body(firstRequest).when().post("/api/transactions")
                .then().statusCode(200);
        Response second = Api.request(Api.GATEWAY).body(secondRequest).when().post("/api/transactions");
        Checks.declined(second, 200, "61", "EXCEEDS_AMOUNT_LIMIT");
    }

    @Test
    void monthlyLimitIsCheckedIndependentlyFromDailyLimit() {
        Map<String, Object> card = Data.card();
        card.put("dailyLimit", 10000);
        card.put("monthlyLimit", 1000);
        String pan = CardFixture.create(card);
        Map<String, Object> firstRequest = Data.authorization(pan);
        firstRequest.put("amount", new BigDecimal("600"));
        Map<String, Object> secondRequest = Data.authorization(pan);
        secondRequest.put("amount", new BigDecimal("600"));
        Api.request(Api.GATEWAY).body(firstRequest).when().post("/api/transactions")
                .then().statusCode(200);
        Response second = Api.request(Api.GATEWAY).body(secondRequest).when().post("/api/transactions");
        Checks.declined(second, 200, "61", "EXCEEDS_AMOUNT_LIMIT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"mti", "stan", "pan", "processingCode", "amount", "currencyCode", "transmissionDateTime",
            "terminalId", "merchantId", "mcc", "acquirerId"})
    void missingRequiredTransactionFieldIsRejected(String field) {
        Map<String, Object> request = Data.authorization("4000001234560001");
        request.remove(field);
        Api.request(Api.GATEWAY).body(request).when().post("/api/transactions").then()
                .statusCode(400).body("error", equalTo("VALIDATION_ERROR"),
                        "serviceName", equalTo("gateway"), "message", containsString(field));
    }

    @ParameterizedTest
    @CsvSource({"pan,400000123456789", "pan,40000012345678901", "pan,400000123456789X",
            "currencyCode,6", "currencyCode,6430", "mcc,541", "mcc,541A"})
    void malformedTransactionFieldIsRejected(String field, String value) {
        Map<String, Object> request = Data.authorization("4000001234560001");
        request.put(field, value);
        Api.request(Api.GATEWAY).body(request).when().post("/api/transactions").then()
                .statusCode(400).body("error", equalTo("VALIDATION_ERROR"),
                        "serviceName", equalTo("gateway"), "message", containsString(field));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void nonPositiveTransactionAmountIsRejected(String amount) {
        Map<String, Object> request = Data.authorization("4000001234560001");
        request.put("amount", new BigDecimal(amount));
        Api.request(Api.GATEWAY).body(request).when().post("/api/transactions").then()
                .statusCode(400).body("error", equalTo("VALIDATION_ERROR"),
                        "serviceName", equalTo("gateway"), "message", containsString("amount"));
    }

    @Test
    void malformedTransmissionDateIsRejected() {
        Map<String, Object> request = Data.authorization("4000001234560001");
        request.put("transmissionDateTime", "not-an- instant");
        Api.request(Api.GATEWAY).body(request).when().post("/api/transactions").then()
                .statusCode(400).body("error", equalTo("VALIDATION_ERROR"),
                        "serviceName", equalTo("gateway"));
    }

    @Test
    void malformedJsonBodyIsRejected() {
        Api.request(Api.GATEWAY).body("{").when().post("/api/transactions").then()
                .statusCode(400).body("error", equalTo("VALIDATION_ERROR"),
                        "serviceName", equalTo("gateway"));
    }

    @Test
    void emptyJsonBodyIsRejected() {
        Api.request(Api.GATEWAY).body("").when().post("/api/transactions").then()
                .statusCode(400).body("error", equalTo("VALIDATION_ERROR"),
                        "serviceName", equalTo("gateway"));
    }
}
