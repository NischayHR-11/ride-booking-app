package com.ridebooking.rideservice.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthControllerTest {

    private final HealthController healthController = new HealthController();

    @Test
    void healthCheck_shouldReturnOk() {
        String response = healthController.healthCheck();
        assertEquals("OK", response);
    }
}
