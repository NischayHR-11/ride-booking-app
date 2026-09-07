package com.ridebooking.rideservice.service;

import com.ridebooking.rideservice.dto.RiderRequestDto;
import com.ridebooking.rideservice.dto.RiderResponseDto;
import com.ridebooking.rideservice.entity.Rider;
import com.ridebooking.rideservice.exception.RiderNotFoundException;
import com.ridebooking.rideservice.repository.RiderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiderServiceTest {

    @Mock
    private RiderRepository riderRepository;

    @InjectMocks
    private RiderService riderService;

    private Rider sampleRider;

    @BeforeEach
    void setUp() {
        sampleRider = Rider.builder()
                .riderId("rider-001")
                .name("Alice Johnson")
                .phone("9876543210")
                .email("alice@example.com")
                .role("RIDER")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void registerRider_shouldSaveAndReturnRider() {
        RiderRequestDto request = RiderRequestDto.builder()
                .name("Alice Johnson")
                .phone("9876543210")
                .email("alice@example.com")
                .build();

        when(riderRepository.existsByPhone("9876543210")).thenReturn(false);
        when(riderRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(riderRepository.save(any(Rider.class))).thenReturn(sampleRider);

        RiderResponseDto response = riderService.registerRider(request);

        assertNotNull(response);
        assertEquals("rider-001", response.getRiderId());
        assertEquals("Alice Johnson", response.getName());
        assertEquals("9876543210", response.getPhone());
        assertEquals("alice@example.com", response.getEmail());
        assertEquals("RIDER", response.getRole());
        verify(riderRepository).save(any(Rider.class));
    }

    @Test
    void registerRider_shouldThrowWhenPhoneDuplicate() {
        RiderRequestDto request = RiderRequestDto.builder()
                .name("Alice Johnson")
                .phone("9876543210")
                .email("alice@example.com")
                .build();

        when(riderRepository.existsByPhone("9876543210")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> riderService.registerRider(request));

        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("9876543210"));
        verify(riderRepository, never()).save(any());
    }

    @Test
    void registerRider_shouldThrowWhenEmailDuplicate() {
        RiderRequestDto request = RiderRequestDto.builder()
                .name("Alice Johnson")
                .phone("9876543210")
                .email("alice@example.com")
                .build();

        when(riderRepository.existsByPhone("9876543210")).thenReturn(false);
        when(riderRepository.existsByEmail("alice@example.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> riderService.registerRider(request));

        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("alice@example.com"));
        verify(riderRepository, never()).save(any());
    }

    @Test
    void getRider_shouldReturnRiderWhenFound() {
        when(riderRepository.findById("rider-001")).thenReturn(Optional.of(sampleRider));

        RiderResponseDto response = riderService.getRider("rider-001");

        assertNotNull(response);
        assertEquals("rider-001", response.getRiderId());
        assertEquals("Alice Johnson", response.getName());
    }

    @Test
    void getRider_shouldThrowWhenNotFound() {
        when(riderRepository.findById("invalid-id")).thenReturn(Optional.empty());

        assertThrows(RiderNotFoundException.class, () -> riderService.getRider("invalid-id"));
    }

    @Test
    void getAllRiders_shouldReturnAllRiders() {
        when(riderRepository.findAll()).thenReturn(java.util.List.of(sampleRider));

        java.util.List<com.ridebooking.rideservice.dto.RiderResponseDto> result = riderService.getAllRiders();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("rider-001", result.get(0).getRiderId());
        assertEquals("Alice Johnson", result.get(0).getName());
        verify(riderRepository).findAll();
    }
}
