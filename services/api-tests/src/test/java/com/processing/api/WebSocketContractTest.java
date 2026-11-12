package com.processing.api;

import org.junit.jupiter.api.Test;

import com.processing.api.support.Api;
import io.restassured.response.Response;

class WebSocketContractTest {
    @Test
    void transactionStreamRouteCurrentResponseIsDocumented() {
        Response response = Api.request(Api.GATEWAY).when().get("/ws/transactions");
        response.then().statusCode(404);
    }

    @Test
    void transactionStreamRouteRemainsUnavailableWithoutUpgrade() {
        Api.request(Api.GATEWAY).header("Accept", "application/json").when()
                .get("/ws/transactions").then().statusCode(404);
    }
}
