package com.processing.api;

import com.processing.api.support.Api;
import io.restassured.response.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.Matchers.*;

class BinLookupApiTest {
    // #TC-N-05
    @ParameterizedTest
    @CsvSource({"400000,ISS001,Test Bank 1", "400001,ISS002,Test Bank 2"})
    void knownBinReturnsIssuer(String bin, String issuerId, String issuerName) {
        // given: BIN exists in the static bin-lookup table
        // when: the lookup service is queried directly, because Gateway has no route for it
        Response response = Api.request(Api.BIN_LOOKUP).when().get("/api/bin/{bin}", bin);
        // then: all fields of BinLookupResponse are part of the HTTP contract
        response.then().statusCode(200).body("bin", equalTo(bin), "issuerId", equalTo(issuerId),
                "issuerName", equalTo(issuerName));
    }

    // #TC-N-06
    @ParameterizedTest
    @CsvSource({"999999,404", "400000,500"})
    void unknownOrFailedLookupReturnsDocumentedErrorStatus(String bin, int expectedStatus) {
        // given: one request addresses an unknown BIN and one asks the service to fail
        Response response = "999999".equals(bin)
                ? Api.request(Api.BIN_LOOKUP).when().get("/api/bin/{bin}", bin)
                : Api.request(Api.BIN_LOOKUP).queryParam("fail", true).when().get("/api/bin/{bin}", bin);
        // then: lookup failure is observable without inspecting the database
        response.then().statusCode(expectedStatus);
    }
}
