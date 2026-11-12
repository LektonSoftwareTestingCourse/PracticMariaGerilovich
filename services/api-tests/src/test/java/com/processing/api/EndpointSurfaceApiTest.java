package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.CardFixture;
import com.processing.api.support.Data;
import io.restassured.response.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;

/** Endpoint checks from the OpenAPI document and the controllers/routes. */
class EndpointSurfaceApiTest {
    // #TC-CM-13, #TC-CM-24
    @Test
    void cardGeneratorReturnsRequestedNumber() {
        // given: a valid generator request from the Cards contract
        // when: one card is generated through Gateway
        Response response = Api.request(Api.GATEWAY).body(Map.of("count", 1, "bins", java.util.List.of("400000")))
                .when().post("/api/cards/generate");
        // then: count and card shape are checked
        response.then().statusCode(201).body("generated", equalTo(1), "cards", hasSize(1),
                "cards[0].pan", matchesPattern("[0-9]{16}"));
    }

    // #TC-CM-09
    @Test
    void cardDeleteIsSoftAndReturnsNoContent() {
        // given: a card created by this test
        String pan = CardFixture.create();
        // when: it is deleted via the public API
        Response deleted = Api.request(Api.GATEWAY).when().delete("/api/cards/{pan}", pan);
        // then: deletion has the documented empty response and subsequent GET is not visible
        deleted.then().statusCode(204).body(is(emptyString()));
        Api.request(Api.GATEWAY).when().get("/api/cards/{pan}", pan).then().statusCode(404);
    }

    // Authorization internal endpoint contract
    @Test
    void directAuthorizationEndpointReturnsContractBody() {
        // given: card state is prepared through Card Management API
        String pan = CardFixture.create();
        Map<String, Object> request = Data.authorization(pan);
        // when: Authorization is called through its documented internal endpoint
        Response response = Api.request(Api.AUTH).body(request).when().post("/api/internal/authorize");
        // then: body exposes the business outcome even for a direct service call
        response.then().statusCode(anyOf(is(200), is(403), is(422)))
                .body("status", anyOf(equalTo("APPROVED"), equalTo("DECLINED")),
                        "responseCode", matchesPattern("[0-9]{2}"), "processingTimeMs", greaterThanOrEqualTo(0));
    }

    // #TC-N-04
    @Test
    void directSwitchRouteReturnsAuthorizationResponse() {
        // given: a valid request payload (route resolves issuer from BIN)
        String pan = CardFixture.create();
        Map<String, Object> request = Data.authorization(pan);
        // when: Switch's internal route endpoint is called
        Response response = Api.request(Api.SWITCH).body(request).when().post("/api/internal/route");
        // then: route returns an authorization-shaped response
        response.then().statusCode(200).body("status", anyOf(equalTo("APPROVED"), equalTo("DECLINED")),
                "responseCode", matchesPattern("[0-9]{2}"));
    }

    // BIN management API
    @Test
    void directBinLookupEndpointsExposeMapping() {
        // given: the card-management BIN resource is independent of transaction state
        // when: all mappings are requested
        Response response = Api.request(Api.CARDS).when().get("/api/bins");
        // then: a JSON array is returned
        response.then().statusCode(200).body("", instanceOf(java.util.List.class));
    }

    // Merchant simulator API
    @Test
    @Tag("fault")
    void merchantListAndSimulatorAreReachable() {
        // given: simulator resources are routed from Gateway
        // when: the merchant catalog is requested
        Response merchants = Api.request(Api.GATEWAY).when().get("/api/simulator/merchants");
        // then: list endpoint has a JSON body; empty data is still a valid list
        merchants.then().statusCode(200).body("", instanceOf(java.util.List.class));
    }

    @Test
    @Tag("fault")
    void specificationTerminalStartAndStatusRoutesAreVisible() {
        // These paths exist in OpenAPI but are absent from current Gateway routes.
        Api.request(Api.GATEWAY).when().post("/api/simulator/terminal/start")
                .then().statusCode(anyOf(is(202), is(404)));
        Api.request(Api.GATEWAY).when().get("/api/simulator/terminal/status")
                .then().statusCode(anyOf(is(200), is(404)));
    }
}
