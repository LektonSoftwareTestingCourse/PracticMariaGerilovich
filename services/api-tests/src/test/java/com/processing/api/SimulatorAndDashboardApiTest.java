package com.processing.api;

import com.processing.api.support.Api;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

class SimulatorAndDashboardApiTest {
    @Test
    void dashboardStatsExposeAllAggregates() {
        Response response = Api.request(Api.GATEWAY).when().get("/api/dashboard/stats");
        response.then().statusCode(200).body("totalTransactions", greaterThanOrEqualTo(0),
                "approvedCount", greaterThanOrEqualTo(0), "declinedCount", greaterThanOrEqualTo(0),
                "approvalRate", allOf(greaterThanOrEqualTo(0.0f), lessThanOrEqualTo(1.0f)),
                "totalAmount", greaterThanOrEqualTo(0), "averageAmount", greaterThanOrEqualTo(0),
                "avgProcessingTimeMs", greaterThanOrEqualTo(0.0f), "transactionsPerMinute", greaterThanOrEqualTo(0));
    }

    @Test
    void dashboardStatsCountsAreInternallyConsistent() {
        Response response = Api.request(Api.GATEWAY).when().get("/api/dashboard/stats");
        response.then().statusCode(200).body("approvalRate",
                allOf(greaterThanOrEqualTo(0.0f), lessThanOrEqualTo(1.0f)));
        int total = response.path("totalTransactions");
        int approved = response.path("approvedCount");
        int declined = response.path("declinedCount");
        assertThat(approved + declined, lessThanOrEqualTo(total));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 501})
    void recentLimitBoundariesAreRejected(int limit) {
        Response response = Api.request(Api.GATEWAY).queryParam("limit", limit)
                .when().get("/api/dashboard/recent");
        response.then().statusCode(400).body("message", containsString("limit"));
    }

    @Test
    void chartsRejectUnknownGranularity() {
        Response response = Api.request(Api.GATEWAY).queryParam("granularity", "minute")
                .when().get("/api/dashboard/charts");
        response.then().statusCode(400).body("message", containsString("granularity"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"normal", "mixed", "high_value", "night_time", "declines_test"})
    void terminalScenarioEnumIsAccepted(String scenario) {
        Map<String, Object> request = Map.of("count", 1, "scenario", scenario, "tps", 1);
        Response response = Api.request(Api.GATEWAY).body(request).when().post("/api/simulator/terminal/run");
        response.then().statusCode(200).body("totalSubmitted", equalTo(1), "approved", greaterThanOrEqualTo(0),
                "declined", greaterThanOrEqualTo(0), "elapsedMs", greaterThanOrEqualTo(0),
                "transactions", hasSize(1));
    }

    @Test
    void simulatorRejectsNonPositiveCount() {
        Response response = Api.request(Api.GATEWAY).body(Map.of("count", 0, "scenario", "normal", "tps", 1))
                .when().post("/api/simulator/terminal/run");
        response.then().statusCode(400).body("message", containsString("count"));
    }

    @Test
    void merchantSimulationReturnsBalancedCounters() {
        Response response = Api.request(Api.GATEWAY).body(Map.of("count", 1, "scenario", "grocery", "tps", 1))
                .when().post("/api/simulator/merchant/run");
        response.then().statusCode(200).body("totalSubmitted", equalTo(1), "approved", greaterThanOrEqualTo(0),
                "declined", greaterThanOrEqualTo(0), "elapsedMs", greaterThanOrEqualTo(0));
    }

    @Test
    void merchantSimulationRejectsUnknownScenario() {
        Api.request(Api.GATEWAY).body(Map.of("count", 1, "scenario", "unknown", "tps", 1))
                .when().post("/api/simulator/merchant/run").then().statusCode(400)
                .body("message", not(emptyOrNullString()));
    }

    @Test
    void recentTransactionsReturnAnArrayWithinRequestedLimit() {
        Api.request(Api.GATEWAY).queryParam("limit", 5).when().get("/api/dashboard/recent")
                .then().statusCode(200).body("", isA(List.class), "size()", lessThanOrEqualTo(5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hour", "day"})
    void chartsAcceptSupportedGranularities(String granularity) {
        Api.request(Api.GATEWAY).queryParam("granularity", granularity).when()
                .get("/api/dashboard/charts").then().statusCode(200).body("", isA(List.class));
    }
}
