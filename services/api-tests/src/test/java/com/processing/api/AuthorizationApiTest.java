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

import java.util.Map;
import java.time.Duration;

import static org.hamcrest.Matchers.*;

class AuthorizationApiTest {
    // #TC-N-11
    @Test
    void approvedTransactionHasGeneratedValuesAndNullDeclineReason() {
        // given: the card is prepared via Cards API, with balance and limits known to this test
        String pan = CardFixture.create();
        Map<String, Object> request = Data.authorization(pan);
        // when: the transaction crosses Gateway -> Switch -> Authorization -> Logger
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/transactions");
        // then: success status, schema, headers, and generated formats are checked
        Checks.approved(response, (String) request.get("stan"));
        response.then().header("Content-Type", containsString("application/json"));
    }

    // #TC-N-07, #TC-N-11
    @Test
    void gatewayTransactionIsEventuallySearchableAndReservesBalance() {
        // given: a new card and one unique authorization request
        String pan = CardFixture.create();
        Map<String, Object> request = Data.authorization(pan);
        String stan = (String) request.get("stan");
        // when: the request is accepted through the complete Gateway path
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/transactions");
        Checks.approved(response, stan);
        // then: Logger becomes searchable asynchronously and the card balance is reduced
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
    @CsvSource({"mti,010", "currencyCode,64", "mcc,541"})
    void oneMalformedFieldIsRejectedWithValidationBody(String field, String value) {
        // given: one field is changed at a time, preserving all other valid fields
        String pan = CardFixture.create();
        Map<String, Object> request = Data.authorization(pan);
        request.put(field, value);
        // when: the malformed authorization request is submitted
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/transactions");
        // then: validation reports the exact field instead of a generic successful response
        response.then().statusCode(400).body("message", containsString(field));
    }

    // Contract validation cases intentionally expose Switch normalization behavior.
    @ParameterizedTest
    @org.junit.jupiter.api.Tag("fault")
    @CsvSource({"stan,1", "terminalId,T", "merchantId,M"})
    void normalizedFieldsShouldBeRejectedByStrictContract(String field, String value) {
        // given: a field shorter than the contract length
        String pan = CardFixture.create();
        Map<String, Object> request = Data.authorization(pan);
        request.put(field, value);
        // when: the request passes through Gateway and Switch
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/transactions");
        // then: strict contract expects a validation response; current implementation normalizes these fields
        response.then().statusCode(400).body("message", containsString(field));
    }

    // #TC-AUTH-35, public decline path
    @Test
    void unknownCardIsADeclineWithCardNotFoundReason() {
        // given: a valid authorization shape and a unique but absent PAN
        Map<String, Object> request = Data.authorization("400000" + Data.digits(10));
        // when: authorization is sent through the public route
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/transactions");
        // then: Switch maps the internal business outcome to the public contract
        Checks.declined(response, 200, "14", "CARD_NOT_FOUND");
    }

    // #TC-AUTH-08, public decline path
    @Test
    void insufficientFundsIsDifferentFromUnknownCard() {
        // given: a card whose explicit balance is lower than the request amount
        Map<String, Object> card = Data.card();
        card.put("initialBalance", 1);
        String pan = CardFixture.create(card);
        Map<String, Object> request = Data.authorization(pan);
        request.put("amount", 100);
        // when: the request is authorized
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/transactions");
        // then: business code and reason are asserted together
        Checks.declined(response, 200, "51", "INSUFFICIENT_FUNDS");
    }

    // #TC-N-12
    @Test
    void blockedCardIsADeclineAndLeavesBalanceUntouched() {
        // given: card state is changed via PATCH, never through the database
        String pan = CardFixture.create();
        CardFixture.patch(pan, Map.of("status", "BLOCKED"));
        Map<String, Object> request = Data.authorization(pan);
        // when: a blocked card is authorized
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/transactions");
        // then: status and reason describe the state path, and no reservation is made
        Checks.declined(response, 200, "05", "CARD_BLOCKED");
        Awaitility.await("blocked card has no reservation side effect")
                .atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(100)).untilAsserted(() ->
                        Api.request(Api.CARDS).when().get("/api/cards/{pan}", pan).then()
                                .statusCode(200).body("availableBalance", equalTo(1000000)));
    }

    // #TC-AUTH-04, public decline path
    @Test
    void expiredCardIsADeclineWithExpirationReason() {
        // given: the known card status is changed to EXPIRED using the public API
        String pan = CardFixture.create();
        CardFixture.patch(pan, Map.of("status", "EXPIRED"));
        Map<String, Object> request = Data.authorization(pan);
        // when: authorization is attempted
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/transactions");
        // then: expiration is distinguished from blocked and inactive states
        Checks.declined(response, 200, "54", "CARD_EXPIRED");
    }
}
