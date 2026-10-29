package com.processing.gateway.downstream;

import com.processing.gateway.properties.GatewayRouteProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DownstreamServiceResolverTest {

    @Test
    void shouldResolveServiceNameForMatchingPath() {
        // Arrange
        GatewayRouteProperties properties = new GatewayRouteProperties();
        properties.setRoutes(List.of(route("authorization", "Path=/api/transactions/**")));
        DownstreamServiceResolver resolver = new DownstreamServiceResolver(properties);

        // Act
        Optional<String> service = resolver.resolve("/api/transactions/123");

        // Assert
        assertThat(service).contains("authorization");
    }

    @Test
    void shouldReturnEmptyWhenNoRouteMatches() {
        // Arrange
        GatewayRouteProperties properties = new GatewayRouteProperties();
        properties.setRoutes(List.of(route("authorization", "Path=/api/transactions/**")));
        DownstreamServiceResolver resolver = new DownstreamServiceResolver(properties);

        // Act
        Optional<String> service = resolver.resolve("/api/cards/1");

        // Assert
        assertThat(service).isEmpty();
    }

    @Test
    void shouldSkipRouteWithoutServiceNameMetadata() {
        // Arrange
        GatewayRouteProperties properties = new GatewayRouteProperties();
        GatewayRouteProperties.RouteDefinition route = new GatewayRouteProperties.RouteDefinition();
        route.setPredicates(List.of("Path=/api/transactions/**"));
        properties.setRoutes(List.of(route));
        DownstreamServiceResolver resolver = new DownstreamServiceResolver(properties);

        // Act
        Optional<String> service = resolver.resolve("/api/transactions/123");

        // Assert
        assertThat(service).isEmpty();
    }

    private GatewayRouteProperties.RouteDefinition route(String serviceName, String pathPredicate) {
        GatewayRouteProperties.RouteDefinition route = new GatewayRouteProperties.RouteDefinition();
        route.setMetadata(Map.of("serviceName", serviceName));
        route.setPredicates(List.of(pathPredicate));
        return route;
    }
}