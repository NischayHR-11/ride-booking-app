package com.ridebooking.rideservice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridebooking.rideservice.dto.RideRequestDto;
import com.ridebooking.rideservice.dto.RideResponseDto;
import com.ridebooking.rideservice.entity.RideStatus;
import com.ridebooking.rideservice.events.NoDriverAvailablePayload;
import com.ridebooking.rideservice.service.RideService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideControllerTest {

    @Mock
    private RideService rideService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RideController rideController;

    @Test
    void createRide_shouldReturnCreatedStatus() {
        RideResponseDto response = RideResponseDto.builder()
                .rideId("ride-123")
                .riderId("rider-001")
                .pickupLocation("Airport")
                .dropLocation("City Center")
                .status(RideStatus.REQUESTED)
                .createdAt(LocalDateTime.now())
                .build();

        when(rideService.createRide(any(RideRequestDto.class))).thenReturn(response);

        RideRequestDto request = RideRequestDto.builder()
                .riderId("rider-001")
                .pickupLocation("Airport")
                .dropLocation("City Center")
                .build();

        ResponseEntity<RideResponseDto> result = rideController.createRide(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals("ride-123", result.getBody().getRideId());
    }

    @Test
    void getRide_shouldReturnOk() {
        RideResponseDto response = RideResponseDto.builder()
                .rideId("ride-123")
                .riderId("rider-001")
                .status(RideStatus.REQUESTED)
                .build();

        when(rideService.getRide("ride-123")).thenReturn(response);

        ResponseEntity<RideResponseDto> result = rideController.getRide("ride-123");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("ride-123", result.getBody().getRideId());
    }

    @Test
    void cancelRide_shouldReturnOk() {
        RideResponseDto response = RideResponseDto.builder()
                .rideId("ride-123")
                .status(RideStatus.CANCELLED)
                .build();

        when(rideService.cancelRide("ride-123")).thenReturn(response);

        ResponseEntity<RideResponseDto> result = rideController.cancelRide("ride-123");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(RideStatus.CANCELLED, result.getBody().getStatus());
    }

    @Test
    void completeRide_shouldReturnOk() {
        RideResponseDto response = RideResponseDto.builder()
                .rideId("ride-123")
                .status(RideStatus.COMPLETED)
                .build();

        when(rideService.completeRide("ride-123")).thenReturn(response);

        ResponseEntity<RideResponseDto> result = rideController.completeRide("ride-123");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(RideStatus.COMPLETED, result.getBody().getStatus());
    }

    @Test
    void getRidesByRider_shouldReturnListOfRides() {
        RideResponseDto ride1 = RideResponseDto.builder()
                .rideId("ride-123")
                .riderId("rider-001")
                .pickupLocation("Airport")
                .status(RideStatus.COMPLETED)
                .build();

        when(rideService.getRidesByRider("rider-001")).thenReturn(List.of(ride1));

        ResponseEntity<List<RideResponseDto>> result = rideController.getRidesByRider("rider-001");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
    }

    @Test
    void rateRide_shouldReturnOk() {
        RideResponseDto response = RideResponseDto.builder()
                .rideId("ride-123")
                .status(RideStatus.COMPLETED)
                .rating(5)
                .feedback("Great!")
                .build();

        when(rideService.rateRide("ride-123", 5, "Great!")).thenReturn(response);

        ResponseEntity<RideResponseDto> result = rideController.rateRide("ride-123",
                Map.of("rating", 5, "feedback", "Great!"));

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(5, result.getBody().getRating());
        assertEquals("Great!", result.getBody().getFeedback());
    }

    @Test
    void getAllRides_shouldReturnListOfRides() {
        RideResponseDto ride1 = RideResponseDto.builder()
                .rideId("ride-123")
                .riderId("rider-001")
                .status(RideStatus.COMPLETED)
                .build();

        when(rideService.getAllRides()).thenReturn(java.util.List.of(ride1));

        ResponseEntity<java.util.List<RideResponseDto>> result = rideController.getAllRides();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
    }

    @Test
    void getRidesByRiderAndStatus_shouldFilterByRider() {
        RideResponseDto ride1 = RideResponseDto.builder()
                .rideId("ride-123")
                .riderId("rider-001")
                .status(RideStatus.COMPLETED)
                .build();

        when(rideService.getRidesByRider("rider-001")).thenReturn(java.util.List.of(ride1));

        ResponseEntity<java.util.List<RideResponseDto>> result = rideController.getRidesByRider("rider-001");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
    }

    @Test
    void createRide_shouldIncludePickupCoordinatesInResponse() {
        RideResponseDto response = RideResponseDto.builder()
                .rideId("ride-123")
                .riderId("rider-001")
                .pickupLocation("Tech Park")
                .dropLocation("Airport Terminal 3")
                .pickupLat(12.97)
                .pickupLng(77.59)
                .estimatedCost(275.50)
                .status(RideStatus.REQUESTED)
                .createdAt(LocalDateTime.now())
                .build();

        when(rideService.createRide(any(RideRequestDto.class))).thenReturn(response);

        RideRequestDto request = RideRequestDto.builder()
                .riderId("rider-001")
                .pickupLocation("Tech Park")
                .dropLocation("Airport Terminal 3")
                .pickupLat(12.97)
                .pickupLng(77.59)
                .build();

        ResponseEntity<RideResponseDto> result = rideController.createRide(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals("ride-123", result.getBody().getRideId());
        assertEquals(12.97, result.getBody().getPickupLat());
        assertEquals(77.59, result.getBody().getPickupLng());
        assertTrue(result.getBody().getEstimatedCost() >= 50 && result.getBody().getEstimatedCost() <= 500,
                "Estimated cost should be between ₹50-₹500");
    }

    @Test
    void handleNoDriverAvailable_webhook_shouldReturnOkWhenPayloadIsValid() throws Exception {
        String rawPayload = "{\"rideId\":\"ride-123\",\"reason\":\"No drivers within 5km\"}";
        NoDriverAvailablePayload payload = new NoDriverAvailablePayload();
        payload.setRideId("ride-123");
        payload.setReason("No drivers within 5km");

        when(objectMapper.readValue(rawPayload, NoDriverAvailablePayload.class)).thenReturn(payload);
        doNothing().when(rideService).handleNoDriverAvailable(payload);

        ResponseEntity<String> result = rideController.handleNoDriverAvailable(rawPayload);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("Processed", result.getBody());
        verify(rideService, times(1)).handleNoDriverAvailable(payload);
    }

    @Test
    void handleNoDriverAvailable_webhook_shouldReturn500OnParseError() throws Exception {
        String rawPayload = "invalid-json";
        when(objectMapper.readValue(rawPayload, NoDriverAvailablePayload.class))
                .thenThrow(new JsonProcessingException("JSON parse error"){});

        ResponseEntity<String> result = rideController.handleNoDriverAvailable(rawPayload);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertEquals("Processing failed", result.getBody());
    }
}
