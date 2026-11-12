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
        Response response = Api.request(Api.BIN_LOOKUP).when().get("/api/bin/{bin}", bin);
        response.then().statusCode(200).body("bin", equalTo(bin), "issuerId", equalTo(issuerId),
                "issuerName", equalTo(issuerName));
    }

    // #TC-N-06
    @ParameterizedTest
    @CsvSource({"999999,404", "400000,500"})
    void unknownOrFailedLookupReturnsDocumentedErrorStatus(String bin, int expectedStatus) {
        Response response = "999999".equals(bin)
                ? Api.request(Api.BIN_LOOKUP).when().get("/api/bin/{bin}", bin)
                : Api.request(Api.BIN_LOOKUP).queryParam("fail", true).when().get("/api/bin/{bin}", bin);
        response.then().statusCode(expectedStatus);
    }
}
