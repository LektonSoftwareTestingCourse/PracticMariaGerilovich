package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.Checks;
import com.processing.api.support.Data;
import io.restassured.response.Response;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.*;

class LoggerApiTest {
    // #TC-N-07
    @Test
    void newTransactionIsStoredAndEventuallySearchable() {
        // given: a self-contained transaction with a unique UUID, STAN and PAN
        Map<String, Object> request = Data.transaction();
        String id = (String) request.get("id");
        String pan = (String) request.get("pan");
        // when: the internal logger endpoint receives the transaction
        Response created = Api.request(Api.LOGGER).body(request).when().post("/api/internal/log");
        // then: creation response contains the generated identifier and status
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

    // API idempotency case from lecture 10
    @Test
    void exactRedeliveryIsIdempotent() {
        // given: one immutable request body and UUID
        Map<String, Object> request = Data.transaction();
        // when: the same message is delivered twice
        Response first = Api.request(Api.LOGGER).body(request).when().post("/api/internal/log");
        Response repeated = Api.request(Api.LOGGER).body(request).when().post("/api/internal/log");
        // then: first delivery creates, exact redelivery returns the existing record
        first.then().statusCode(201).body("status", equalTo("stored"));
        repeated.then().statusCode(200).body("id", equalTo(request.get("id")), "status", equalTo("APPROVED"));
    }

    // API idempotency conflict case from lecture 10
    @Test
    void sameIdWithDifferentDataIsAConflict() {
        // given: a valid message and a copy with only the amount changed
        Map<String, Object> original = Data.transaction();
        Map<String, Object> conflicting = new java.util.LinkedHashMap<>(original);
        conflicting.put("amount", 999);
        // when: both messages use the same id
        Api.request(Api.LOGGER).body(original).when().post("/api/internal/log").then().statusCode(201);
        Response conflict = Api.request(Api.LOGGER).body(conflicting).when().post("/api/internal/log");
        // then: the consumer is told that the same identity has different data
        Checks.error(conflict, 409, "transaction-logger", "already exists");
    }

    // API search pagination and GPath case
    @Test
    void searchSupportsGPathFilterAndPaginationBoundaries() {
        // given: two transactions with one common merchant and distinct PANs
        String merchant = "M" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        Map<String, Object> first = Data.transaction();
        Map<String, Object> second = Data.transaction();
        first.put("merchantId", merchant);
        second.put("merchantId", merchant);
        Api.request(Api.LOGGER).body(first).when().post("/api/internal/log").then().statusCode(201);
        Api.request(Api.LOGGER).body(second).when().post("/api/internal/log").then().statusCode(201);
        // when: query filters and requests one page
        Response response = Api.request(Api.GATEWAY).queryParam("merchantId", merchant)
                .queryParam("limit", 1).queryParam("offset", 0).when().get("/api/transactions/search");
        // then: page size is bounded and GPath selects only our records
        response.then().statusCode(200).body("transactions", hasSize(1),
                "transactions.findAll { it.merchantId == '%s' }.size()".formatted(merchant), equalTo(1));
    }

    // API export contract case
    @Test
    void exportReturnsCsvContentDisposition() {
        // given: no state dependency is needed for an empty export
        // when: CSV export is requested
        Response response = Api.request(Api.GATEWAY).when().get("/api/transactions/export");
        // then: format and download header are contract fields
        response.then().statusCode(200).contentType(containsString("text/csv"))
                .header("Content-Disposition", containsString("transactions.csv"));
    }
}
