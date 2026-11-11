package com.processing.api.support;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

/** HTTP addresses are configured here only; system properties override environment variables. */
public final class Api {
    public static final RequestSpecification GATEWAY = spec("gateway", 8080);
    public static final RequestSpecification CARDS = spec("cards", 8081);
    public static final RequestSpecification SWITCH = spec("switch", 8082);
    public static final RequestSpecification AUTH = spec("auth", 8083);
    public static final RequestSpecification MERCHANT = spec("merchant", 8084);
    public static final RequestSpecification TERMINAL = spec("terminal", 8085);
    public static final RequestSpecification LOGGER = spec("logger", 8088);
    public static final RequestSpecification BIN_LOOKUP = spec("bin-lookup", 8096);
    public static final RequestSpecification NOTIFICATION = spec("notification", 8097);

    private Api() { }

    public static RequestSpecification request(RequestSpecification target) {
        return given().spec(target).log().ifValidationFails();
    }

    private static RequestSpecification spec(String name, int port) {
        String url = System.getProperty("api." + name + ".url",
                System.getenv().getOrDefault("API_" + name.replace('-', '_').toUpperCase(java.util.Locale.ROOT) + "_URL",
                        "http://localhost:" + port));
        return new RequestSpecBuilder().setBaseUri(url)
                .setContentType(ContentType.JSON).setAccept(ContentType.JSON)
                .setConfig(RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", 5000)
                        .setParam("http.socket.timeout", 30000)))
                .build();
    }
}
