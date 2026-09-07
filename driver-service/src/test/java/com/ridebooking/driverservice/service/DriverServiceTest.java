package com.ridebooking.driverservice.service;

import com.ridebooking.driverservice.dto.DriverRegisterDto;
import com.ridebooking.driverservice.dto.DriverResponseDto;
import com.ridebooking.driverservice.dto.LocationUpdateDto;
import com.ridebooking.driverservice.entity.Driver;
import com.ridebooking.driverservice.events.DriverEventPublisher;
import com.ridebooking.driverservice.events.RideRequestedEventPayload;
import com.ridebooking.driverservice.repository.DriverRepository;
import com.ridebooking.driverservice.exception.DriverNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private DriverEventPublisher driverEventPublisher;

    @Mock
    private DriverMatchingStrategy matchingStrategy;

    @InjectMocks
    private DriverService driverService;

    private Driver sampleDriver;

    @BeforeEach
    void setUp() {
        sampleDriver = Driver.builder()
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
    void registerDriver_shouldSaveAndReturnResponse() {
        DriverRegisterDto dto = DriverRegisterDto.builder()
                .name("Ravi Kumar")
                .phone("9876543210")
                .vehicleNumber("KA01AB1234")
                .vehicleType("SEDAN")
                .latitude(12.97)
                .longitude(77.59)
                .build();

        when(driverRepository.existsByPhone("9876543210")).thenReturn(false);
        when(driverRepository.existsByVehicleNumber("KA01AB1234")).thenReturn(false);
        when(driverRepository.save(any(Driver.class))).thenReturn(sampleDriver);

        DriverResponseDto response = driverService.registerDriver(dto);

        assertNotNull(response);
        assertEquals("driver-001", response.getDriverId());
        assertEquals("Ravi Kumar", response.getName());
        assertEquals("9876543210", response.getPhone());
        verify(driverRepository).save(any(Driver.class));
    }

    @Test
    void registerDriver_shouldThrowWhenPhoneAlreadyExists() {
        DriverRegisterDto dto = DriverRegisterDto.builder()
                .name("Ravi Kumar")
                .phone("9876543210")
                .vehicleNumber("KA01AB1234")
                .vehicleType("SEDAN")
                .build();

        when(driverRepository.existsByPhone("9876543210")).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> driverService.registerDriver(dto));
        verify(driverRepository, never()).save(any());
    }

    @Test
    void registerDriver_shouldThrowWhenVehicleNumberAlreadyExists() {
        DriverRegisterDto dto = DriverRegisterDto.builder()
                .name("Ravi Kumar")
                .phone("9999999999")
                .vehicleNumber("KA01AB1234")
                .vehicleType("SEDAN")
                .build();

        when(driverRepository.existsByPhone("9999999999")).thenReturn(false);
        when(driverRepository.existsByVehicleNumber("KA01AB1234")).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> driverService.registerDriver(dto));
        verify(driverRepository, never()).save(any());
    }

    @Test
    void getDriver_shouldReturnDriver() {
        when(driverRepository.findById("driver-001")).thenReturn(Optional.of(sampleDriver));

        DriverResponseDto response = driverService.getDriver("driver-001");

        assertNotNull(response);
        assertEquals("driver-001", response.getDriverId());
        assertEquals("Ravi Kumar", response.getName());
    }

    @Test
    void getDriver_shouldThrowWhenNotFound() {
        when(driverRepository.findById("invalid")).thenReturn(Optional.empty());

        assertThrows(DriverNotFoundException.class, () -> driverService.getDriver("invalid"));
    }

    @Test
    void updateAvailability_shouldUpdateAndReturn() {
        when(driverRepository.findById("driver-001")).thenReturn(Optional.of(sampleDriver));
        when(driverRepository.save(any(Driver.class))).thenReturn(sampleDriver);

        DriverResponseDto response = driverService.updateAvailability("driver-001", false);

        assertNotNull(response);
        assertFalse(sampleDriver.isAvailable());
        verify(driverRepository).save(sampleDriver);
    }

    @Test
    void updateAvailability_shouldThrowWhenDriverNotFound() {
        when(driverRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThrows(DriverNotFoundException.class,
                () -> driverService.updateAvailability("unknown", false));
    }

    @Test
    void updateLocation_shouldUpdateCoordinates() {
        LocationUpdateDto dto = new LocationUpdateDto(13.01, 77.65);

        when(driverRepository.findById("driver-001")).thenReturn(Optional.of(sampleDriver));
        when(driverRepository.save(any(Driver.class))).thenReturn(sampleDriver);

        DriverResponseDto response = driverService.updateLocation("driver-001", dto);

        assertNotNull(response);
        assertEquals(13.01, sampleDriver.getLatitude());
        assertEquals(77.65, sampleDriver.getLongitude());
        verify(driverRepository).save(sampleDriver);
    }

    @Test
    void updateLocation_shouldThrowWhenDriverNotFound() {
        LocationUpdateDto dto = new LocationUpdateDto(13.01, 77.65);

        when(driverRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThrows(DriverNotFoundException.class,
                () -> driverService.updateLocation("unknown", dto));
    }

    @Test
    void handleRideRequested_shouldAssignAvailableDriver() {
        RideRequestedEventPayload payload = new RideRequestedEventPayload();
        payload.setRideId("ride-123");
        payload.setRiderId("rider-001");
        payload.setPickupLocation("Tech Park");
        payload.setDropLocation("Airport");
        payload.setPickupLat(12.97);
        payload.setPickupLng(77.59);

        Driver availableDriver = Driver.builder()
                .driverId("driver-001")
                .name("Ravi Kumar")
                .available(true)
                .latitude(12.96)
                .longitude(77.60)
                .rating(4.8)
                .build();

        when(driverRepository.findByAvailableTrue()).thenReturn(List.of(availableDriver));
        when(matchingStrategy.findBestDriver(any(), eq(12.97), eq(77.59)))
                .thenReturn(Optional.of(availableDriver));
        when(driverRepository.save(any(Driver.class))).thenReturn(availableDriver);

        driverService.handleRideRequested(payload);

        assertFalse(availableDriver.isAvailable());
        verify(driverEventPublisher).publishDriverAssigned(any());
        verify(driverRepository).save(availableDriver);
    }

    @Test
    void handleRideRequested_shouldPublishNoDriverAvailableWhenNoMatch() {
        RideRequestedEventPayload payload = new RideRequestedEventPayload();
        payload.setRideId("ride-123");
        payload.setRiderId("rider-001");
        payload.setPickupLocation("Remote Location");
        payload.setDropLocation("Airport");
        payload.setPickupLat(15.0);
        payload.setPickupLng(80.0);

        when(driverRepository.findByAvailableTrue()).thenReturn(List.of());
        when(matchingStrategy.findBestDriver(any(), eq(15.0), eq(80.0)))
                .thenReturn(Optional.empty());

        driverService.handleRideRequested(payload);

        verify(driverEventPublisher).publishNoDriverAvailable(any());
    }

    @Test
    void handleRideRequested_shouldUsePickupCoordinatesForMatching() {
        RideRequestedEventPayload payload = new RideRequestedEventPayload();
        payload.setRideId("ride-123");
        payload.setRiderId("rider-001");
        payload.setPickupLat(12.9);
        payload.setPickupLng(77.6);

        Driver driver = Driver.builder()
                .driverId("driver-001")
                .available(true)
                .latitude(12.95)
                .longitude(77.65)
                .build();

        when(driverRepository.findByAvailableTrue()).thenReturn(List.of(driver));
        when(matchingStrategy.findBestDriver(List.of(driver), 12.9, 77.6))
                .thenReturn(Optional.of(driver));
        when(driverRepository.save(any(Driver.class))).thenReturn(driver);

        driverService.handleRideRequested(payload);

        // Verify that matching strategy was called with correct coordinates
        verify(matchingStrategy).findBestDriver(List.of(driver), 12.9, 77.6);
    }

    @Test
    void handleRideRequested_shouldNotAssignUnavailableDriver() {
        RideRequestedEventPayload payload = new RideRequestedEventPayload();
        payload.setRideId("ride-123");
        payload.setRiderId("rider-001");
        payload.setPickupLat(12.97);
        payload.setPickupLng(77.59);

        when(driverRepository.findByAvailableTrue()).thenReturn(List.of());
        when(matchingStrategy.findBestDriver(List.of(), 12.97, 77.59))
                .thenReturn(Optional.empty());

        driverService.handleRideRequested(payload);

        verify(driverEventPublisher).publishNoDriverAvailable(any());
    }
}
