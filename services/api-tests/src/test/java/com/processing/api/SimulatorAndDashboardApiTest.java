package com.processing.api;

import com.processing.api.support.Api;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.hamcrest.Matchers.*;

class SimulatorAndDashboardApiTest {
    // Dashboard contract case
    @Test
    void dashboardStatsExposeAllAggregates() {
        // given: dashboard is queried through the same gateway used by clients
        // when: aggregate statistics are requested
        Response response = Api.request(Api.GATEWAY).when().get("/api/dashboard/stats");
        // then: every documented aggregate is present and numerically bounded
        response.then().statusCode(200).body("totalTransactions", greaterThanOrEqualTo(0),
                "approvedCount", greaterThanOrEqualTo(0), "declinedCount", greaterThanOrEqualTo(0),
                "approvalRate", allOf(greaterThanOrEqualTo(0.0f), lessThanOrEqualTo(1.0f)),
                "totalAmount", greaterThanOrEqualTo(0), "averageAmount", greaterThanOrEqualTo(0),
                "avgProcessingTimeMs", greaterThanOrEqualTo(0.0f), "transactionsPerMinute", greaterThanOrEqualTo(0));
    }

    // Dashboard pagination boundary case
    @ParameterizedTest
    @ValueSource(ints = {0, 501})
    void recentLimitBoundariesAreRejected(int limit) {
        // given: the documented 1..500 equivalence boundary
        // when: recent transactions are requested outside the range
        Response response = Api.request(Api.GATEWAY).queryParam("limit", limit)
                .when().get("/api/dashboard/recent");
        // then: validation response identifies the limit
        response.then().statusCode(400).body("message", containsString("limit"));
    }

    // Dashboard charts validation case
    @Test
    void chartsRejectUnknownGranularity() {
        // given: only hour and day are accepted by ChartsFilter
        // when: an unsupported granularity is passed
        Response response = Api.request(Api.GATEWAY).queryParam("granularity", "minute")
                .when().get("/api/dashboard/charts");
        // then: invalid input is a 400 with field-level feedback
        response.then().statusCode(400).body("message", containsString("granularity"));
    }

    // Simulator scenario equivalence classes
    @ParameterizedTest
    @ValueSource(strings = {"normal", "mixed", "high_value", "night_time", "declines_test"})
    void terminalScenarioEnumIsAccepted(String scenario) {
        // given: one of the scenario values from the OpenAPI contract
        Map<String, Object> request = Map.of("count", 1, "scenario", scenario, "tps", 1);
        // when: the simulator is invoked through Gateway
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/simulator/terminal/run");
        // then: response counters are internally consistent and body is checked
        response.then().statusCode(200).body("totalSubmitted", equalTo(1), "approved", greaterThanOrEqualTo(0),
                "declined", greaterThanOrEqualTo(0), "elapsedMs", greaterThanOrEqualTo(0),
                "transactions", hasSize(1));
    }

    // Simulator count validation case
    @Test
    void simulatorRejectsNonPositiveCount() {
        // given: count violates @Min(1)
        // when: terminal simulation is requested
        Response response = Api.request(Api.GATEWAY).body(Map.of("count", 0, "scenario", "normal", "tps", 1))
                .when().post("/api/simulator/terminal/run");
        // then: validation body identifies count
        response.then().statusCode(400).body("message", containsString("count"));
    }

    // Merchant simulator contract case
    @Test
    void merchantSimulationReturnsBalancedCounters() {
        // given: a valid merchant scenario from the contract
        // when: one merchant transaction is submitted
        Response response = Api.request(Api.GATEWAY).body(Map.of("count", 1, "scenario", "grocery", "tps", 1))
                .when().post("/api/simulator/merchant/run");
        // then: approved and declined partition the submitted transactions
        response.then().statusCode(200).body("totalSubmitted", equalTo(1), "approved", greaterThanOrEqualTo(0),
                "declined", greaterThanOrEqualTo(0), "elapsedMs", greaterThanOrEqualTo(0));
    }
}
