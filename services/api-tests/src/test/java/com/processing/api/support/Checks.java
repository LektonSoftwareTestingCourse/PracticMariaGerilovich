package com.processing.api.support;

import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import java.math.BigDecimal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;

public final class Checks {
    private Checks() { }

    public static ValidatableResponse approved(Response response, String stan) {
        return response.then().log().ifValidationFails().statusCode(200)
                .contentType("application/json")
                .body(matchesJsonSchemaInClasspath("schemas/authorization.json"))
                .body("stan", equalTo(stan), "responseCode", equalTo("00"), "status", equalTo("APPROVED"),
                        "rrn", matchesPattern("[0-9]{12}"), "authCode", matchesPattern("[A-Z0-9]{6}"),
                        "declineReason", nullValue(), "processingTimeMs", greaterThanOrEqualTo(0));
    }

    public static ValidatableResponse declined(Response response, int httpStatus, String code, String reason) {
        return response.then().log().ifValidationFails().statusCode(httpStatus)
                .body("status", equalTo("DECLINED"), "responseCode", equalTo(code),
                        "declineReason", equalTo(reason), "authCode", nullValue(), "rrn", nullValue());
    }

    public static ValidatableResponse error(Response response, int status, String service, String message) {
        return response.then().log().ifValidationFails().statusCode(status)
                .body("serviceName", equalTo(service), "error", not(emptyOrNullString()),
                        "message", containsString(message), "timestamp", notNullValue());
    }

    public static void money(Response response, String field, String expected) {
        assertThat(response.jsonPath().getObject(field, BigDecimal.class),
                comparesEqualTo(new BigDecimal(expected)));
    }
}
