package com.ridebooking.rideservice.controller;

import com.ridebooking.rideservice.dto.RiderRequestDto;
import com.ridebooking.rideservice.dto.RiderResponseDto;
import com.ridebooking.rideservice.service.RiderService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiderControllerTest {

    @Mock
    private RiderService riderService;

    @InjectMocks
    private RiderController riderController;

    @Test
    void registerRider_shouldReturnCreatedStatus() {
        RiderResponseDto response = RiderResponseDto.builder()
                .riderId("rider-001")
                .name("Alice Johnson")
                .phone("9876543210")
                .email("alice@example.com")
                .role("RIDER")
                .createdAt(LocalDateTime.now())
                .build();

        when(riderService.registerRider(any(RiderRequestDto.class))).thenReturn(response);

        RiderRequestDto request = RiderRequestDto.builder()
                .name("Alice Johnson")
                .phone("9876543210")
                .email("alice@example.com")
                .build();

        ResponseEntity<RiderResponseDto> result = riderController.registerRider(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("rider-001", result.getBody().getRiderId());
        assertEquals("Alice Johnson", result.getBody().getName());
        verify(riderService).registerRider(any(RiderRequestDto.class));
    }

    @Test
    void getRider_shouldReturnOkWithRiderDetails() {
        RiderResponseDto response = RiderResponseDto.builder()
                .riderId("rider-001")
                .name("Alice Johnson")
                .phone("9876543210")
                .email("alice@example.com")
                .role("RIDER")
                .createdAt(LocalDateTime.now())
                .build();

        when(riderService.getRider("rider-001")).thenReturn(response);

        ResponseEntity<RiderResponseDto> result = riderController.getRider("rider-001");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("rider-001", result.getBody().getRiderId());
        verify(riderService).getRider("rider-001");
    }

    @Test
    void getAllRiders_shouldReturnListOfRiders() {
        RiderResponseDto response = RiderResponseDto.builder()
                .riderId("rider-001")
                .name("Alice Johnson")
                .phone("9876543210")
                .email("alice@example.com")
                .role("RIDER")
                .createdAt(LocalDateTime.now())
                .build();

        when(riderService.getAllRiders()).thenReturn(List.of(response));

        ResponseEntity<List<RiderResponseDto>> result = riderController.getAllRiders();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
        assertEquals("rider-001", result.getBody().get(0).getRiderId());
        verify(riderService).getAllRiders();
    }
}
