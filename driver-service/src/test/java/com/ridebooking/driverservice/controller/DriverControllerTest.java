package com.ridebooking.driverservice.controller;

import com.ridebooking.driverservice.dto.DriverRegisterDto;
import com.ridebooking.driverservice.dto.DriverResponseDto;
import com.ridebooking.driverservice.dto.LocationUpdateDto;
import com.ridebooking.driverservice.service.DriverService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverControllerTest {

    @Mock
    private DriverService driverService;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private DriverController driverController;

    private DriverResponseDto sampleResponse() {
        return DriverResponseDto.builder()
                .driverId("driver-001")
                .name("Ravi Kumar")
                .phone("9876543210")
                .vehicleNumber("KA01AB1234")
                .vehicleType("SEDAN")
                .latitude(12.97)
                .longitude(77.59)
                .available(true)
                .rating(5.0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void registerDriver_shouldReturnCreatedStatus() {
        DriverRegisterDto dto = DriverRegisterDto.builder()
                .name("Ravi Kumar")
                .phone("9876543210")
                .vehicleNumber("KA01AB1234")
                .vehicleType("SEDAN")
                .build();

        when(driverService.registerDriver(any(DriverRegisterDto.class))).thenReturn(sampleResponse());

        ResponseEntity<DriverResponseDto> result = driverController.registerDriver(dto);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("driver-001", result.getBody().getDriverId());
    }

    @Test
    void getDriver_shouldReturnOk() {
        when(driverService.getDriver("driver-001")).thenReturn(sampleResponse());

        ResponseEntity<DriverResponseDto> result = driverController.getDriver("driver-001");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("driver-001", result.getBody().getDriverId());
    }

    @Test
    void updateAvailability_shouldReturnOk() {
        DriverResponseDto updated = sampleResponse();
        updated.setAvailable(false);

        when(driverService.updateAvailability("driver-001", false)).thenReturn(updated);

        ResponseEntity<DriverResponseDto> result = driverController.updateAvailability(
                "driver-001", Map.of("available", false));

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertFalse(result.getBody().isAvailable());
    }

    @Test
    void updateLocation_shouldReturnOk() {
        LocationUpdateDto dto = new LocationUpdateDto(13.01, 77.65);

        when(driverService.updateLocation(eq("driver-001"), any(LocationUpdateDto.class)))
                .thenReturn(sampleResponse());

        ResponseEntity<DriverResponseDto> result = driverController.updateLocation("driver-001", dto);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("driver-001", result.getBody().getDriverId());
    }

    @Test
    void getAvailableDrivers_shouldReturnOk() {
        when(driverService.getAvailableDrivers()).thenReturn(List.of(sampleResponse()));

        ResponseEntity<List<DriverResponseDto>> result = driverController.getAvailableDrivers();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
    }

    @Test
    void handleRideRequestedWebhook_shouldReturnOkOnValidPayload() throws Exception {
        String rawPayload = "{\"rideId\":\"ride-123\",\"riderId\":\"rider-001\","
                + "\"pickupLocation\":\"Tech Park\",\"dropLocation\":\"Airport\","
                + "\"pickupLat\":12.97,\"pickupLng\":77.59}";

        com.ridebooking.driverservice.events.RideRequestedEventPayload payload =
                new com.ridebooking.driverservice.events.RideRequestedEventPayload();
        payload.setRideId("ride-123");
        payload.setRiderId("rider-001");
        payload.setPickupLocation("Tech Park");
        payload.setDropLocation("Airport");
        payload.setPickupLat(12.97);
        payload.setPickupLng(77.59);

        when(objectMapper.readValue(rawPayload,
                com.ridebooking.driverservice.events.RideRequestedEventPayload.class))
                .thenReturn(payload);

        ResponseEntity<String> result = driverController.handleRideRequestedWebhook(rawPayload);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("Processed", result.getBody());
        verify(driverService).handleRideRequested(payload);
    }
}
