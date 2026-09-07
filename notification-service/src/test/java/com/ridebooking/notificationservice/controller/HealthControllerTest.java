package com.ridebooking.notificationservice.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthControllerTest {

    @Test
    void healthCheck_returnsOk() {
        HealthController controller = new HealthController();

        String result = controller.healthCheck();

        assertEquals("OK", result);
    }
}
