package com.processing.api.support;

import io.restassured.response.Response;
import java.util.Map;

import static org.hamcrest.Matchers.*;

public final class CardFixture {
    private CardFixture() { }

    public static String create() {
        return create(Data.card());
    }

    public static String create(Map<String, Object> body) {
        Response response = Api.request(Api.GATEWAY).body(body).when().post("/api/cards");
        response.then().statusCode(201).body("pan", matchesPattern("[0-9]{16}"),
                "id", notNullValue(), "status", equalTo("ACTIVE"), "bin", equalTo(body.get("bin")));
        return response.path("pan");
    }

    public static void patch(String pan, Map<String, Object> changes) {
        Api.request(Api.GATEWAY).body(changes).when().patch("/api/cards/{pan}", pan)
                .then().statusCode(200).body("pan", equalTo(pan));
    }

    public static Response read(String pan) {
        return Api.request(Api.CARDS).when().get("/api/cards/{pan}", pan);
    }
}
