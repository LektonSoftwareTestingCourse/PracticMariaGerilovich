package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.Checks;
import com.processing.api.support.Data;
import io.restassured.response.Response;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.*;

class LoggerApiTest {
    // #TC-N-07
    @Test
    void newTransactionIsStoredAndEventuallySearchable() {
        Map<String, Object> request = Data.transaction();
        String id = (String) request.get("id");
        String pan = (String) request.get("pan");
        Response created = Api.request(Api.LOGGER).body(request).when().post("/api/internal/log");
        created.then().statusCode(201).body("id", equalTo(id), "status", equalTo("stored"));
        AtomicReference<Response> search = new AtomicReference<>();
        Awaitility.await("transaction becomes searchable")
                .atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
                    Response current = Api.request(Api.GATEWAY).queryParam("pan", pan).queryParam("limit", 10)
                            .when().get("/api/transactions/search");
                    search.set(current);
                    current.then().statusCode(200)
                            .body("transactions.findAll { it.id == '%s' }.size()".formatted(id), equalTo(1),
                                    "transactions.find { it.id == '%s' }.pan".formatted(id), equalTo(pan));
                });
        search.get().then().body("transactions.findAll { it.id == '%s' }.size()".formatted(id), equalTo(1));
    }

    @Test
    void exactRedeliveryIsIdempotent() {
        Map<String, Object> request = Data.transaction();
        Response first = Api.request(Api.LOGGER).body(request).when().post("/api/internal/log");
        Response repeated = Api.request(Api.LOGGER).body(request).when().post("/api/internal/log");
        first.then().statusCode(201).body("status", equalTo("stored"));
        repeated.then().statusCode(200).body("id", equalTo(request.get("id")), "status", equalTo("APPROVED"));
    }

    @Test
    void sameIdWithDifferentDataIsAConflict() {
        Map<String, Object> original = Data.transaction();
        Map<String, Object> conflicting = new java.util.LinkedHashMap<>(original);
        conflicting.put("amount", 999);
        Api.request(Api.LOGGER).body(original).when().post("/api/internal/log").then().statusCode(201);
        Response conflict = Api.request(Api.LOGGER).body(conflicting).when().post("/api/internal/log");
        Checks.error(conflict, 409, "transaction-logger", "already exists");
    }

    @Test
    void searchSupportsGPathFilterAndPaginationBoundaries() {
        String merchant = "M" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        Map<String, Object> first = Data.transaction();
        Map<String, Object> second = Data.transaction();
        first.put("merchantId", merchant);
        second.put("merchantId", merchant);
        Api.request(Api.LOGGER).body(first).when().post("/api/internal/log").then().statusCode(201);
        Api.request(Api.LOGGER).body(second).when().post("/api/internal/log").then().statusCode(201);
        Response response = Api.request(Api.GATEWAY).queryParam("merchantId", merchant)
                .queryParam("limit", 1).queryParam("offset", 0).when().get("/api/transactions/search");
        response.then().statusCode(200).body("transactions", hasSize(1),
                "transactions.findAll { it.merchantId == '%s' }.size()".formatted(merchant), equalTo(1));
    }

    @Test
    void exportReturnsCsvContentDisposition() {
        Response response = Api.request(Api.GATEWAY).when().get("/api/transactions/export");
        response.then().statusCode(200).contentType(containsString("text/csv"))
                .header("Content-Disposition", containsString("transactions.csv"));
    }

    @Test
    void filteredExportStillReturnsCsvContract() {
        Api.request(Api.GATEWAY).queryParam("status", "APPROVED").queryParam("limit", 1).when()
                .get("/api/transactions/export").then().statusCode(200)
                .contentType(containsString("text/csv"))
                .header("Content-Disposition", containsString("transactions.csv"));
    }

    @Test
    void searchSupportsPanStatusDatesAndEmptyResult() {
        Map<String, Object> request = Data.transaction();
        String pan = (String) request.get("pan");
        Api.request(Api.LOGGER).body(request).when().post("/api/internal/log").then().statusCode(201);
        String date = LocalDate.now(ZoneOffset.UTC).toString();
        Api.request(Api.GATEWAY).queryParam("pan", pan).queryParam("status", "APPROVED")
                .queryParam("dateFrom", date).queryParam("dateTo", date).when()
                .get("/api/transactions/search").then().statusCode(200)
                .body("total", greaterThanOrEqualTo(1), "transactions", not(empty()));
        Api.request(Api.GATEWAY).queryParam("pan", "4000000000000000").when()
                .get("/api/transactions/search").then().statusCode(200)
                .body("total", equalTo(0), "transactions", empty());
    }

    @Test
    void searchSupportsIssuerMccAndDeclineReasonFilters() {
        Map<String, Object> request = Data.transaction();
        request.put("issuerId", "ISS001");
        request.put("mcc", "5411");
        request.put("status", "DECLINED");
        request.put("declineReason", "CARD_BLOCKED");
        String id = (String) request.get("id");
        Api.request(Api.LOGGER).body(request).when().post("/api/internal/log").then().statusCode(201);

        Api.request(Api.GATEWAY).queryParam("issuerId", "ISS001").when()
                .get("/api/transactions/search").then().statusCode(200)
                .body("transactions.findAll { it.id == '%s' }.size()".formatted(id), equalTo(1));
        Api.request(Api.GATEWAY).queryParam("mcc", "5411").when()
                .get("/api/transactions/search").then().statusCode(200)
                .body("transactions.findAll { it.id == '%s' }.size()".formatted(id), equalTo(1));
        Api.request(Api.GATEWAY).queryParam("declineReason", "CARD_BLOCKED").when()
                .get("/api/transactions/search").then().statusCode(200)
                .body("transactions.findAll { it.id == '%s' }.size()".formatted(id), equalTo(1));
    }

    @Test
    void searchRejectsInvalidPaginationAndDateRange() {
        Api.request(Api.GATEWAY).queryParam("limit", 0).when().get("/api/transactions/search")
                .then().statusCode(400);
        Api.request(Api.GATEWAY).queryParam("limit", -1).when().get("/api/transactions/search")
                .then().statusCode(400);
        Api.request(Api.GATEWAY).queryParam("limit", 501).when().get("/api/transactions/search")
                .then().statusCode(400);
        Api.request(Api.GATEWAY).queryParam("offset", -1).when().get("/api/transactions/search")
                .then().statusCode(400);
        Api.request(Api.GATEWAY).queryParam("dateFrom", "2026-12-31")
                .queryParam("dateTo", "2026-01-01").when().get("/api/transactions/search")
                .then().statusCode(400);
        Api.request(Api.GATEWAY).queryParam("status", "UNKNOWN").when().get("/api/transactions/search")
                .then().statusCode(400);
    }

    @Test
    void invalidLoggerBodyIdentifiesStanRule() {
        Map<String, Object> request = Data.transaction();
        request.put("stan", "1");
        Api.request(Api.LOGGER).body(request).when().post("/api/internal/log").then()
                .statusCode(400).body("message", containsString("stan"));
    }

    @Test
    void invalidLoggerPanIsRejected() {
        Map<String, Object> request = Data.transaction();
        request.put("pan", "123");
        Api.request(Api.LOGGER).body(request).when().post("/api/internal/log").then()
                .statusCode(400).body("message", not(emptyOrNullString()));
    }

    @Test
    void missingLoggerIdIsRejected() {
        Map<String, Object> request = Data.transaction();
        request.remove("id");
        Api.request(Api.LOGGER).body(request).when().post("/api/internal/log").then()
                .statusCode(400).body("message", not(emptyOrNullString()));
    }

    @Test
    void nonPositiveLoggerAmountIsRejected() {
        Map<String, Object> request = Data.transaction();
        request.put("amount", 0);
        Api.request(Api.LOGGER).body(request).when().post("/api/internal/log").then()
                .statusCode(400).body("message", not(emptyOrNullString()));
    }

    @Test
    void unknownLoggerStatusIsRejected() {
        Map<String, Object> request = Data.transaction();
        request.put("status", "UNKNOWN");
        Api.request(Api.LOGGER).body(request).when().post("/api/internal/log").then()
                .statusCode(400).body("message", not(emptyOrNullString()));
    }
}
