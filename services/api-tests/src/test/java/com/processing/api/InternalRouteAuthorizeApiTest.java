package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.CardFixture;
import com.processing.api.support.Data;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;

class InternalRouteAuthorizeApiTest {
    @Test
    void routeReturnsAuthorizationResponseForKnownBin() {
        String pan = CardFixture.create();
        Response response = Api.request(Api.SWITCH).body(Data.authorization(pan))
                .when().post("/api/internal/route");
        response.then().statusCode(200)
                .body("mti", matchesPattern("[0-9]{4}"), "status",
                        anyOf(equalTo("APPROVED"), equalTo("DECLINED")),
                        "responseCode", matchesPattern("[0-9]{2}"));
    }

    @Test
    void routeReturnsCardNotFoundForUnknownBin() {
        Response response = Api.request(Api.SWITCH).body(Data.authorization("999999" + Data.digits(10)))
                .when().post("/api/internal/route");
        response.then().statusCode(200).body("status", equalTo("DECLINED"),
                "responseCode", equalTo("14"), "declineReason", equalTo("CARD_NOT_FOUND"));
    }

    @Test
    void routeValidationFailureIsReturnedAsResponseBody() {
        Map<String, Object> request = new LinkedHashMap<>(Data.authorization("4000001234560001"));
        request.put("pan", "invalid");
        Response response = Api.request(Api.SWITCH).body(request).when().post("/api/internal/route");
        response.then().statusCode(200).body("status", equalTo("DECLINED"),
                "responseCode", matchesPattern("[0-9]{2}"), "declineReason", notNullValue());
    }

    @Test
    void authorizeApprovedReturns200() {
        String pan = CardFixture.create();
        Response response = Api.request(Api.AUTH).body(Data.authorization(pan))
                .when().post("/api/internal/authorize");
        response.then().statusCode(200).body("status", equalTo("APPROVED"),
                "responseCode", equalTo("00"), "declineReason", nullValue(),
                "rrn", matchesPattern("[0-9]{12}"), "authCode", matchesPattern("[A-Z0-9]{6}"));
    }

    @Test
    void authorizeUnknownCardReturns404() {
        Response response = Api.request(Api.AUTH).body(Data.authorization("400000" + Data.digits(10)))
                .when().post("/api/internal/authorize");
        response.then().statusCode(404).body("status", equalTo("DECLINED"),
                "responseCode", equalTo("14"), "declineReason", equalTo("CARD_NOT_FOUND"));
    }

    @Test
    void authorizeBlockedCardReturns403() {
        String pan = CardFixture.create();
        CardFixture.patch(pan, Map.of("status", "BLOCKED"));
        Response response = Api.request(Api.AUTH).body(Data.authorization(pan))
                .when().post("/api/internal/authorize");
        response.then().statusCode(403).body("status", equalTo("DECLINED"),
                "responseCode", equalTo("05"), "declineReason", equalTo("CARD_BLOCKED"));
    }

    @Test
    void authorizeInsufficientFundsReturns422() {
        Map<String, Object> card = new LinkedHashMap<>(Data.card());
        card.put("initialBalance", 1);
        String pan = CardFixture.create(card);
        Map<String, Object> request = Data.authorization(pan);
        request.put("amount", 100);
        Response response = Api.request(Api.AUTH).body(request).when().post("/api/internal/authorize");
        response.then().statusCode(422).body("status", equalTo("DECLINED"),
                "responseCode", equalTo("51"), "declineReason", equalTo("INSUFFICIENT_FUNDS"));
    }

    @Test
    void authorizeMalformedRequestReturns400() {
        Map<String, Object> request = new LinkedHashMap<>(Data.authorization("4000001234560001"));
        request.put("mti", "010");
        Response response = Api.request(Api.AUTH).body(request).when().post("/api/internal/authorize");
        response.then().statusCode(400).body("status", equalTo(400), "error", equalTo("Bad Request"),
                "path", equalTo("/api/internal/authorize"));
    }
}
