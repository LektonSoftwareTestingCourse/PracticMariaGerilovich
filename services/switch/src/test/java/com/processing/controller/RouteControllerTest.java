package com.processing.controller;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.service.RouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteControllerTest {

    private static final String PAN = "4000001234567893";
    private static final String STAN = "000001";

    @Mock
    private RouteService routeService;

    private RouteController controller;

    @BeforeEach
    void setUp() {
        controller = new RouteController(routeService);
    }

    @Test
    void shouldReturnRoutedResponseFromRouteService() {
        // Arrange
        AuthorizationResponse response = approvedResponse();
        when(routeService.route(any())).thenReturn(response);

        // Act
        ResponseEntity<AuthorizationResponse> entity = controller.routeTransaction(request(PAN));

        // Assert
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody()).isEqualTo(response);
    }

    @Test
    void shouldReturnRoutedResponseWhenPanIsNull() {
        // Arrange
        AuthorizationResponse response = approvedResponse();
        when(routeService.route(any())).thenReturn(response);

        // Act
        ResponseEntity<AuthorizationResponse> entity = controller.routeTransaction(request(null));

        // Assert
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody()).isEqualTo(response);
    }

    @Test
    void shouldReturnRoutedResponseWhenPanIsShort() {
        // Arrange
        AuthorizationResponse response = approvedResponse();
        when(routeService.route(any())).thenReturn(response);

        // Act
        ResponseEntity<AuthorizationResponse> entity = controller.routeTransaction(request("1234"));

        // Assert
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody()).isEqualTo(response);
    }

    private AuthorizationResponse approvedResponse() {
        return new AuthorizationResponse(
                "0100", STAN, "000000000001", "A1B2C3",
                AuthorizationResponse.CODE_APPROVED, AuthorizationResponse.STATUS_APPROVED,
                null, 10);
    }

    private AuthorizationRequest request(String pan) {
        return new AuthorizationRequest(
                "0100", STAN, pan, "000000",
                new BigDecimal("10000"), "643", Instant.now(),
                "TERM0001", "POS", "MERCH0000000002", "5045", "ACQ002", "ISS001");
    }
}
