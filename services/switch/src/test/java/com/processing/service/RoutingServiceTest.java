package com.processing.service;

import com.processing.config.SwitchProperties;
import com.processing.exception.UnknownBinException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

    private static final Map<String, String> BIN_TABLE = Map.of("400000", "ISS001");

    @Mock
    private SwitchProperties switchProperties;

    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        when(switchProperties.binRouting()).thenReturn(BIN_TABLE);
        routingService = new RoutingService(switchProperties);
    }

    @Test
    void shouldReturnIssuerIdForKnownBin() {
        // Act
        String issuerId = routingService.getIssuerIdByPan("4000001234567893");

        // Assert
        assertThat(issuerId).isEqualTo("ISS001");
    }

    @ParameterizedTest(name = "pan={0}")
    @MethodSource("invalidPans")
    void shouldThrowWhenPanCannotBeRouted(String pan) {
        // Act + Assert
        assertThatThrownBy(() -> routingService.getIssuerIdByPan(pan))
                .isInstanceOf(UnknownBinException.class);
    }

    private static Stream<Arguments> invalidPans() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of("12345"),
                Arguments.of("9999991234567893"));
    }
}