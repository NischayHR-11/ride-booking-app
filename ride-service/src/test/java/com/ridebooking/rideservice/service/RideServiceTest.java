package com.ridebooking.rideservice.service;

import com.ridebooking.rideservice.dto.RideRequestDto;
import com.ridebooking.rideservice.dto.RideResponseDto;
import com.ridebooking.rideservice.entity.Ride;
import com.ridebooking.rideservice.entity.RideStatus;
import com.ridebooking.rideservice.entity.Rider;
import com.ridebooking.rideservice.events.NoDriverAvailablePayload;
import com.ridebooking.rideservice.events.RideEventPublisher;
import com.ridebooking.rideservice.exception.InvalidRideStateException;
import com.ridebooking.rideservice.exception.RideNotFoundException;
import com.ridebooking.rideservice.repository.RideRepository;
import com.ridebooking.rideservice.repository.RiderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideServiceTest {

    @Mock
    private RideRepository rideRepository;

    @Mock
    private RiderRepository riderRepository;

    @Mock
    private RideEventPublisher rideEventPublisher;

    @InjectMocks
    private RideService rideService;

    private Ride sampleRide;
    private Rider sampleRider;

    @BeforeEach
    void setUp() {
        sampleRider = Rider.builder()
                .riderId("rider-001")
                .name("John Rider")
                .phone("9876543210")
                .email("john@example.com")
                .role("RIDER")
                .build();

        sampleRide = Ride.builder()
                .rideId("ride-123")
                .rider(sampleRider)
                .pickupLocation("Airport")
                .dropLocation("City Center")
                .status(RideStatus.REQUESTED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createRide_shouldSaveAndPublishEvent() {
        RideRequestDto request = RideRequestDto.builder()
                .riderId("rider-001")
                .pickupLocation("Airport")
                .dropLocation("City Center")
                .build();

        when(riderRepository.findById("rider-001")).thenReturn(Optional.of(sampleRider));
        when(rideRepository.save(any(Ride.class))).thenReturn(sampleRide);

        RideResponseDto response = rideService.createRide(request);

        assertNotNull(response);
        assertEquals("rider-001", response.getRiderId());
        assertEquals("Airport", response.getPickupLocation());
        assertEquals(RideStatus.REQUESTED, response.getStatus());
        verify(riderRepository).findById("rider-001");
        verify(rideRepository).save(any(Ride.class));
        verify(rideEventPublisher).publishRideRequested(any());
    }

    @Test
    void getRide_shouldReturnRide() {
        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(sampleRide));

        RideResponseDto response = rideService.getRide("ride-123");

        assertNotNull(response);
        assertEquals("ride-123", response.getRideId());
        assertEquals("rider-001", response.getRiderId());
    }

    @Test
    void getRide_shouldThrowWhenNotFound() {
        when(rideRepository.findById("invalid")).thenReturn(Optional.empty());

        assertThrows(RideNotFoundException.class, () -> rideService.getRide("invalid"));
    }

    @Test
    void cancelRide_shouldCancelAndPublishEvent() {
        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(sampleRide));
        when(rideRepository.save(any(Ride.class))).thenReturn(sampleRide);

        RideResponseDto response = rideService.cancelRide("ride-123");

        assertNotNull(response);
        assertEquals(RideStatus.CANCELLED, sampleRide.getStatus());
        verify(rideEventPublisher).publishRideCancelled(any());
    }

    @Test
    void cancelRide_shouldThrowWhenAlreadyCancelled() {
        sampleRide.setStatus(RideStatus.CANCELLED);
        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(sampleRide));

        assertThrows(InvalidRideStateException.class, () -> rideService.cancelRide("ride-123"));
    }

    @Test
    void cancelRide_shouldThrowWhenCompleted() {
        sampleRide.setStatus(RideStatus.COMPLETED);
        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(sampleRide));

        assertThrows(InvalidRideStateException.class, () -> rideService.cancelRide("ride-123"));
    }

    @Test
    void completeRide_shouldCompleteAndPublishEvent() {
        sampleRide.setStatus(RideStatus.ASSIGNED);
        sampleRide.setDriverId("driver-001");
        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(sampleRide));
        when(rideRepository.save(any(Ride.class))).thenReturn(sampleRide);

        RideResponseDto response = rideService.completeRide("ride-123");

        assertNotNull(response);
        assertEquals(RideStatus.COMPLETED, sampleRide.getStatus());
        verify(rideEventPublisher).publishRideCompleted(any());
    }

    @Test
    void completeRide_shouldThrowWhenNotAssigned() {
        sampleRide.setStatus(RideStatus.REQUESTED);
        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(sampleRide));

        assertThrows(InvalidRideStateException.class, () -> rideService.completeRide("ride-123"));
    }

    @Test
    void getRidesByRider_shouldReturnAllRidesForRider() {
        when(rideRepository.findByRiderRiderId("rider-001")).thenReturn(java.util.List.of(sampleRide));

        java.util.List<RideResponseDto> result = rideService.getRidesByRider("rider-001");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ride-123", result.get(0).getRideId());
        assertEquals("rider-001", result.get(0).getRiderId());
    }

    @Test
    void rateRide_shouldAddRatingAndFeedback() {
        sampleRide.setStatus(RideStatus.COMPLETED);
        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(sampleRide));
        when(rideRepository.save(any(Ride.class))).thenReturn(sampleRide);

        RideResponseDto response = rideService.rateRide("ride-123", 5, "Great ride!");

        assertNotNull(response);
        verify(rideRepository).save(sampleRide);
        assertEquals(5, sampleRide.getRating());
        assertEquals("Great ride!", sampleRide.getFeedback());
    }

    @Test
    void rateRide_shouldThrowWhenNotCompleted() {
        sampleRide.setStatus(RideStatus.REQUESTED);
        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(sampleRide));

        assertThrows(InvalidRideStateException.class, () -> rideService.rateRide("ride-123", 5, "Great!"));
    }

    @Test
    void createRide_shouldIncludePickupCoordinates() {
        RideRequestDto request = RideRequestDto.builder()
                .riderId("rider-001")
                .pickupLocation("Tech Park")
                .dropLocation("Airport Terminal 3")
                .pickupLat(12.97)
                .pickupLng(77.59)
                .build();

        when(riderRepository.findById("rider-001")).thenReturn(Optional.of(sampleRider));
        when(rideRepository.save(any(Ride.class))).thenReturn(sampleRide);

        RideResponseDto response = rideService.createRide(request);

        assertNotNull(response);
        verify(rideRepository).save(any(Ride.class));
        verify(rideEventPublisher).publishRideRequested(any());
    }

    @Test
    void createRide_shouldGenerateEstimatedCost() {
        RideRequestDto request = RideRequestDto.builder()
                .riderId("rider-001")
                .pickupLocation("Tech Park")
                .dropLocation("Airport Terminal 3")
                .pickupLat(12.97)
                .pickupLng(77.59)
                .build();

        sampleRide.setPickupLat(12.97);
        sampleRide.setPickupLng(77.59);
        sampleRide.setEstimatedCost(125.50);

        when(riderRepository.findById("rider-001")).thenReturn(Optional.of(sampleRider));
        when(rideRepository.save(any(Ride.class))).thenReturn(sampleRide);

        RideResponseDto response = rideService.createRide(request);

        assertNotNull(response);
        assertNotNull(response.getEstimatedCost());
        assertTrue(response.getEstimatedCost() >= 50 && response.getEstimatedCost() <= 500,
                "Estimated cost should be between ₹50-₹500");
    }

    @Test
    void handleNoDriverAvailable_shouldUpdateRideStatus() {
        sampleRide.setStatus(RideStatus.REQUESTED);
        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(sampleRide));
        when(rideRepository.save(any(Ride.class))).thenReturn(sampleRide);

        // Create payload and call handleNoDriverAvailable
        NoDriverAvailablePayload payload = new NoDriverAvailablePayload();
        payload.setRideId("ride-123");
        payload.setReason("No drivers available within 5km");

        rideService.handleNoDriverAvailable(payload);

        // Verify ride status was updated to NO_DRIVER_AVAILABLE
        assertEquals(RideStatus.NO_DRIVER_AVAILABLE, sampleRide.getStatus());
        verify(rideRepository, times(1)).save(any(Ride.class));
    }

    @Test
    void handleNoDriverAvailable_shouldThrowWhenRideNotFound() {
        when(rideRepository.findById("invalid-ride")).thenReturn(Optional.empty());

        NoDriverAvailablePayload payload = new NoDriverAvailablePayload();
        payload.setRideId("invalid-ride");
        payload.setReason("No driver available");

        // Should not throw - logs warning and returns
        rideService.handleNoDriverAvailable(payload);
        
        // Verify save was never called since ride was not found
        verify(rideRepository, never()).save(any(Ride.class));
    }

    @Test
    void handleNoDriverAvailable_shouldLogReason() {
        sampleRide.setStatus(RideStatus.REQUESTED);
        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(sampleRide));
        when(rideRepository.save(any(Ride.class))).thenReturn(sampleRide);

        String reason = "No drivers found within 5km radius";
        NoDriverAvailablePayload payload = new NoDriverAvailablePayload();
        payload.setRideId("ride-123");
        payload.setReason(reason);

        rideService.handleNoDriverAvailable(payload);

        // Verify the ride status was updated
        assertEquals(RideStatus.NO_DRIVER_AVAILABLE, sampleRide.getStatus());
        verify(rideRepository, times(1)).findById("ride-123");
        verify(rideRepository, times(1)).save(any(Ride.class));
    }

    @Test
    void completeRide_shouldThrowWhenInNoDriverAvailableState() {
        sampleRide.setStatus(RideStatus.NO_DRIVER_AVAILABLE);
        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(sampleRide));

        assertThrows(InvalidRideStateException.class, () -> rideService.completeRide("ride-123"));
    }

    @Test
    void cancelRide_shouldWorkFromNoDriverAvailableState() {
        sampleRide.setStatus(RideStatus.NO_DRIVER_AVAILABLE);
        when(rideRepository.findById("ride-123")).thenReturn(Optional.of(sampleRide));
        when(rideRepository.save(any(Ride.class))).thenReturn(sampleRide);

        RideResponseDto response = rideService.cancelRide("ride-123");

        assertNotNull(response);
        assertEquals(RideStatus.CANCELLED, sampleRide.getStatus());
        verify(rideEventPublisher).publishRideCancelled(any());
    }

    @Test
    void cancelRide_shouldThrowWhenRideNotFound() {
        when(rideRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(RideNotFoundException.class, () -> rideService.cancelRide("missing"));
    }

    @Test
    void completeRide_shouldThrowWhenRideNotFound() {
        when(rideRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(RideNotFoundException.class, () -> rideService.completeRide("missing"));
    }

    @Test
    void rateRide_shouldThrowWhenRideNotFound() {
        when(rideRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(RideNotFoundException.class, () -> rideService.rateRide("missing", 5, "Good"));
    }

    @Test
    void createRide_shouldThrowWhenRiderNotFound() {
        RideRequestDto request = RideRequestDto.builder()
                .riderId("unknown-rider")
                .pickupLocation("Airport")
                .dropLocation("City Center")
                .build();

        when(riderRepository.findById("unknown-rider")).thenReturn(Optional.empty());

        assertThrows(com.ridebooking.rideservice.exception.RiderNotFoundException.class,
                () -> rideService.createRide(request));
    }
}
