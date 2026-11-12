package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.CardFixture;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

class EndpointSurfaceApiTest {
    @Test
    void cardGeneratorReturnsRequestedNumber() {
        Response response = Api.request(Api.GATEWAY).body(Map.of("count", 1, "bins", java.util.List.of("400000")))
                .when().post("/api/cards/generate");
        response.then().statusCode(201).body("generated", equalTo(1), "cards", hasSize(1),
                "cards[0].pan", matchesPattern("[0-9]{16}"));
    }

    @Test
    void cardDeleteIsSoftAndReturnsNoContent() {
        String pan = CardFixture.create();
        Response deleted = Api.request(Api.GATEWAY).when().delete("/api/cards/{pan}", pan);
        deleted.then().statusCode(204).body(is(emptyString()));
        Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan).then().statusCode(404);
    }

    @Test
    void directBinLookupEndpointsExposeMapping() {
        Response response = Api.request(Api.CARDS).when().get("/api/bins");
        response.then().statusCode(200).body("", instanceOf(java.util.List.class));
    }

    @Test
    void directBinLookupReturnsKnownIssuerMapping() {
        Api.request(Api.CARDS).when().get("/api/bins/{bin}", "400000").then()
                .statusCode(200).body("bin", equalTo("400000"), "issuerId", not(emptyOrNullString()));
    }

    @Test
    void merchantListAndSimulatorAreReachable() {
        Response merchants = Api.request(Api.MERCHANT).when().get("/api/simulator/merchants");
        merchants.then().statusCode(200).body("", instanceOf(java.util.List.class));
    }

    @Test
    void merchantListContainsContractFields() {
        Api.request(Api.MERCHANT).when().get("/api/simulator/merchants").then().statusCode(200)
                .body("every { it.id != null && it.name != null && it.mcc != null }", equalTo(true));
    }
}
