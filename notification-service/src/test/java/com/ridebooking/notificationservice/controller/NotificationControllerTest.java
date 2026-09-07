package com.ridebooking.notificationservice.controller;

import com.ridebooking.notificationservice.dto.NotificationResponseDto;
import com.ridebooking.notificationservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private NotificationController notificationController;

    @Test
    void getNotifications_shouldReturnOkWithList() {
        NotificationResponseDto dto = NotificationResponseDto.builder()
                .notificationId("notif-001")
                .userId("driver-001")
                .message("Driver driver-001 assigned to ride ride-123")
                .type("DRIVER_ASSIGNED")
                .sent(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationService.getNotifications("driver-001")).thenReturn(List.of(dto));

        ResponseEntity<List<NotificationResponseDto>> result =
                notificationController.getNotifications("driver-001");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
        assertEquals("notif-001", result.getBody().get(0).getNotificationId());
    }

    @Test
    void getNotifications_shouldReturnEmptyListWhenNone() {
        when(notificationService.getNotifications("unknown")).thenReturn(List.of());

        ResponseEntity<List<NotificationResponseDto>> result =
                notificationController.getNotifications("unknown");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertTrue(result.getBody().isEmpty());
    }

    @Test
    void handleDriverAssigned_shouldReturnOkOnValidPayload() throws Exception {
        String rawPayload = "{\"eventType\":\"DRIVER_ASSIGNED\",\"rideId\":\"ride-123\","
                + "\"driverId\":\"driver-001\",\"vehicleNumber\":\"KA01AB1234\",\"eta\":10}";

        com.ridebooking.notificationservice.events.DriverAssignedPayload payload =
                new com.ridebooking.notificationservice.events.DriverAssignedPayload(
                        "DRIVER_ASSIGNED", "ride-123", "driver-001", "KA01AB1234", 10, null);

        when(objectMapper.readValue(rawPayload,
                com.ridebooking.notificationservice.events.DriverAssignedPayload.class))
                .thenReturn(payload);

        ResponseEntity<String> result = notificationController.handleDriverAssigned(rawPayload);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("OK", result.getBody());
        verify(notificationService).handleDriverAssigned(payload);
    }

    @Test
    void handleRideCancelled_shouldReturnOkOnValidPayload() throws Exception {
        String rawPayload = "{\"eventType\":\"RIDE_CANCELLED\",\"rideId\":\"ride-123\",\"riderId\":\"rider-001\"}";

        com.ridebooking.notificationservice.events.RideCancelledPayload payload =
                new com.ridebooking.notificationservice.events.RideCancelledPayload(
                        "RIDE_CANCELLED", "ride-123", "rider-001", null);

        when(objectMapper.readValue(rawPayload,
                com.ridebooking.notificationservice.events.RideCancelledPayload.class))
                .thenReturn(payload);

        ResponseEntity<String> result = notificationController.handleRideCancelled(rawPayload);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("OK", result.getBody());
        verify(notificationService).handleRideCancelled(payload);
    }

    @Test
    void handleRideCompleted_shouldReturnOkOnValidPayload() throws Exception {
        String rawPayload = "{\"eventType\":\"RIDE_COMPLETED\",\"rideId\":\"ride-123\","
                + "\"driverId\":\"driver-001\",\"riderId\":\"rider-001\"}";

        com.ridebooking.notificationservice.events.RideCompletedPayload payload =
                new com.ridebooking.notificationservice.events.RideCompletedPayload(
                        "RIDE_COMPLETED", "ride-123", "driver-001", "rider-001", null);

        when(objectMapper.readValue(rawPayload,
                com.ridebooking.notificationservice.events.RideCompletedPayload.class))
                .thenReturn(payload);

        ResponseEntity<String> result = notificationController.handleRideCompleted(rawPayload);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("OK", result.getBody());
        verify(notificationService).handleRideCompleted(payload);
    }

    @Test
    void handleDriverAssigned_shouldReturn500OnParseError() throws Exception {
        when(objectMapper.readValue(any(String.class),
                eq(com.ridebooking.notificationservice.events.DriverAssignedPayload.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("parse error"){});

        ResponseEntity<String> result = notificationController.handleDriverAssigned("invalid-json");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertEquals("Failed", result.getBody());
    }

    @Test
    void getAllNotifications_shouldReturnOk() {
        when(notificationService.getAllNotifications()).thenReturn(List.of());

        ResponseEntity<List<NotificationResponseDto>> result = notificationController.getAllNotifications();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
    }
}
