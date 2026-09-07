package com.ridebooking.rideservice.service;

import com.ridebooking.rideservice.dto.RideRequestDto;
import com.ridebooking.rideservice.dto.RideResponseDto;
import com.ridebooking.rideservice.entity.Ride;
import com.ridebooking.rideservice.entity.RideStatus;
import com.ridebooking.rideservice.entity.Rider;
import com.ridebooking.rideservice.events.DriverAssignedPayload;
import com.ridebooking.rideservice.events.NoDriverAvailablePayload;
import com.ridebooking.rideservice.events.RideCancelledEvent;
import com.ridebooking.rideservice.events.RideCompletedEvent;
import com.ridebooking.rideservice.events.RideEventPublisher;
import com.ridebooking.rideservice.events.RideRequestedEvent;
import com.ridebooking.rideservice.exception.InvalidRideStateException;
import com.ridebooking.rideservice.exception.RideNotFoundException;
import com.ridebooking.rideservice.exception.RiderNotFoundException;
import com.ridebooking.rideservice.repository.RideRepository;
import com.ridebooking.rideservice.repository.RiderRepository;
import in.zeta.spectra.capture.SpectraLogger;
import lombok.RequiredArgsConstructor;
import olympus.trace.OlympusSpectra;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class RideService {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(RideService.class);
    private static final String RIDE_ID_ATTR = "rideId";

    private final RideRepository rideRepository;
    private final RiderRepository riderRepository;
    private final RideEventPublisher rideEventPublisher;

    public RideResponseDto createRide(RideRequestDto request) {
        Rider rider = riderRepository.findById(request.getRiderId())
                .orElseThrow(() -> new RiderNotFoundException(request.getRiderId()));

        // Estimated fare based on route (simulates external pricing API response)
        int routeHash = (request.getPickupLocation() + request.getDropLocation()).hashCode();
        double estimatedCost = Math.round((50 + ((routeHash % 450 + 450) % 450)) * 100.0) / 100.0;

        Ride ride = Ride.builder()
                .rider(rider)
                .pickupLocation(request.getPickupLocation())
                .dropLocation(request.getDropLocation())
                .pickupLat(request.getPickupLat())
                .pickupLng(request.getPickupLng())
                .estimatedCost(estimatedCost)
                .status(RideStatus.REQUESTED)
                .build();

        ride = rideRepository.save(ride);

        logger.info("[RideService] Ride created")
                .attr(RIDE_ID_ATTR, ride.getRideId())
                .attr("riderId", ride.getRider().getRiderId())
                .attr("pickup", ride.getPickupLocation())
                .attr("drop", ride.getDropLocation())
                .log();

        RideRequestedEvent event = RideRequestedEvent.of(
                ride.getRideId(), ride.getRider().getRiderId(),
                ride.getPickupLocation(), ride.getDropLocation(),
                ride.getPickupLat(), ride.getPickupLng()
        );
        rideEventPublisher.publishRideRequested(event);

        return mapToResponse(ride);
    }

    @Transactional(readOnly = true)
    public RideResponseDto getRide(String rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));
        return mapToResponse(ride);
    }

    public RideResponseDto cancelRide(String rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        if (ride.getStatus() == RideStatus.COMPLETED || ride.getStatus() == RideStatus.CANCELLED) {
            throw new InvalidRideStateException("Cannot cancel a ride in status: " + ride.getStatus());
        }

        ride.setStatus(RideStatus.CANCELLED);
        rideRepository.save(ride);

        logger.info("[RideService] Ride cancelled").attr(RIDE_ID_ATTR, rideId).log();

        rideEventPublisher.publishRideCancelled(
                RideCancelledEvent.of(ride.getRideId(), ride.getRider().getRiderId())
        );

        return mapToResponse(ride);
    }

    public RideResponseDto completeRide(String rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        if (ride.getStatus() != RideStatus.ASSIGNED && ride.getStatus() != RideStatus.STARTED) {
            throw new InvalidRideStateException("Cannot complete a ride in status: " + ride.getStatus());
        }

        ride.setStatus(RideStatus.COMPLETED);
        rideRepository.save(ride);

        logger.info("[RideService] Ride completed").attr(RIDE_ID_ATTR, rideId).log();

        rideEventPublisher.publishRideCompleted(
                RideCompletedEvent.of(ride.getRideId(), ride.getDriverId(), ride.getRider().getRiderId())
        );

        return mapToResponse(ride);
    }

    @Transactional(readOnly = true)
    public List<RideResponseDto> getAllRides() {
        return rideRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RideResponseDto> getRidesByRider(String riderId) {
        return rideRepository.findByRiderRiderId(riderId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public void handleDriverAssigned(DriverAssignedPayload payload) {
        logger.info("[RideService] Consumed DRIVER_ASSIGNED event")
                .attr(RIDE_ID_ATTR, payload.getRideId())
                .attr("driverId", payload.getDriverId())
                .log();

        Optional<Ride> optionalRide = rideRepository.findById(payload.getRideId());
        if (optionalRide.isEmpty()) {
            logger.warn("[RideService] Ride not found for DRIVER_ASSIGNED event")
                    .attr(RIDE_ID_ATTR, payload.getRideId())
                    .log();
            return;
        }
        Ride ride = optionalRide.get();

        ride.setDriverId(payload.getDriverId());
        ride.setStatus(RideStatus.ASSIGNED);
        rideRepository.save(ride);

        logger.info("[RideService] Ride status updated to ASSIGNED")
                .attr(RIDE_ID_ATTR, payload.getRideId())
                .attr("driverId", payload.getDriverId())
                .log();
    }

    public void handleNoDriverAvailable(NoDriverAvailablePayload payload) {
        logger.info("[RideService] Consumed NO_DRIVER_AVAILABLE event")
                .attr(RIDE_ID_ATTR, payload.getRideId())
                .attr("reason", payload.getReason())
                .log();

        Optional<Ride> optionalRide = rideRepository.findById(payload.getRideId());
        if (optionalRide.isEmpty()) {
            logger.warn("[RideService] Ride not found for NO_DRIVER_AVAILABLE event")
                    .attr(RIDE_ID_ATTR, payload.getRideId())
                    .log();
            return;
        }
        Ride ride = optionalRide.get();

        ride.setStatus(RideStatus.NO_DRIVER_AVAILABLE);
        rideRepository.save(ride);

        logger.info("[RideService] Ride status updated to NO_DRIVER_AVAILABLE")
                .attr(RIDE_ID_ATTR, payload.getRideId())
                .log();
    }

    public RideResponseDto rateRide(String rideId, int rating, String feedback) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));
        
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new InvalidRideStateException("Can only rate completed rides");
        }
        
        ride.setRating(rating);
        ride.setFeedback(feedback);
        rideRepository.save(ride);
        
        logger.info("[RideService] Ride rated")
                .attr(RIDE_ID_ATTR, rideId)
                .attr("rating", rating)
                .log();
        
        return mapToResponse(ride);
    }

    private RideResponseDto mapToResponse(Ride ride) {
        return RideResponseDto.builder()
                .rideId(ride.getRideId())
                .riderId(ride.getRider().getRiderId())
                .riderName(ride.getRider().getName())
                .driverId(ride.getDriverId())
                .pickupLocation(ride.getPickupLocation())
                .dropLocation(ride.getDropLocation())
                .pickupLat(ride.getPickupLat())
                .pickupLng(ride.getPickupLng())
                .estimatedCost(ride.getEstimatedCost())
                .status(ride.getStatus())
                .rating(ride.getRating())
                .feedback(ride.getFeedback())
                .createdAt(ride.getCreatedAt())
                .updatedAt(ride.getUpdatedAt())
                .build();
    }
}
