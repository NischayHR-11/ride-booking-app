package com.ridebooking.rideservice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridebooking.rideservice.dto.RideRequestDto;
import com.ridebooking.rideservice.dto.RideResponseDto;
import com.ridebooking.rideservice.events.DriverAssignedPayload;
import com.ridebooking.rideservice.events.NoDriverAvailablePayload;
import com.ridebooking.rideservice.provider.RideProvider;
import com.ridebooking.rideservice.service.RideService;
import in.zeta.springframework.boot.commons.authorization.sandboxAccessControl.SandboxAuthorizedSync;
import in.zeta.spectra.capture.SpectraLogger;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import olympus.trace.OlympusSpectra;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
@Tag(name = "Ride API", description = "Manage ride lifecycle — create, view, cancel, and complete rides")
public class RideController {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(RideController.class);

    private final RideService rideService;
    private final ObjectMapper objectMapper;

    @PostMapping
    @Operation(summary = "Request a new ride", description = "Creates a ride and publishes RIDE_REQUESTED event to Atropos")
    @SandboxAuthorizedSync(action = "ride.request", object = "1@" + RideProvider.OBJECT_TYPE + ".ridebooking.app", tenantID = "1001034")
    public ResponseEntity<RideResponseDto> createRide(@Valid @RequestBody RideRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rideService.createRide(request));
    }

    @GetMapping("/{rideId}")
    @Operation(summary = "Get ride details")
    @SandboxAuthorizedSync(action = "ride.list", object = "1@" + RideProvider.OBJECT_TYPE + ".ridebooking.app", tenantID = "1001034")
    public ResponseEntity<RideResponseDto> getRide(@PathVariable String rideId) {
        return ResponseEntity.ok(rideService.getRide(rideId));
    }

    @PostMapping("/{rideId}/cancel")
    @Operation(summary = "Cancel a ride", description = "Cancels ride and publishes RIDE_CANCELLED event to Atropos")
    @SandboxAuthorizedSync(action = "ride.cancel", object = "1@" + RideProvider.OBJECT_TYPE + ".ridebooking.app", tenantID = "1001034")
    public ResponseEntity<RideResponseDto> cancelRide(@PathVariable String rideId) {
        return ResponseEntity.ok(rideService.cancelRide(rideId));
    }

    @PostMapping("/{rideId}/complete")
    @Operation(summary = "Complete a ride", description = "Completes ride and publishes RIDE_COMPLETED event to Atropos")
    @SandboxAuthorizedSync(action = "ride.complete", object = "1@" + RideProvider.OBJECT_TYPE + ".ridebooking.app", tenantID = "1001034")
    public ResponseEntity<RideResponseDto> completeRide(@PathVariable String rideId) {
        return ResponseEntity.ok(rideService.completeRide(rideId));
    }

    @GetMapping
    @Operation(summary = "Get all rides for a rider")
    @SandboxAuthorizedSync(action = "ride.list", object = "1@" + RideProvider.OBJECT_TYPE + ".ridebooking.app", tenantID = "1001034")
    public ResponseEntity<List<RideResponseDto>> getRidesByRider(@RequestParam String riderId) {
        return ResponseEntity.ok(rideService.getRidesByRider(riderId));
    }

    @GetMapping("/all")
    @Operation(summary = "Get all rides")
    @SandboxAuthorizedSync(action = "ride.list", object = "1@" + RideProvider.OBJECT_TYPE + ".ridebooking.app", tenantID = "1001034")
    public ResponseEntity<List<RideResponseDto>> getAllRides() {
        return ResponseEntity.ok(rideService.getAllRides());
    }

    /**
     * Webhook endpoint called by Atropos when DRIVER_ASSIGNED event fires from Driver Service.
     */
    @Hidden
    @PostMapping("/events/driver-assigned/webhook")
    public ResponseEntity<String> handleDriverAssigned(@RequestBody String rawPayload) {
        logger.info("[RideController] Webhook received — DRIVER_ASSIGNED").log();
        try {
            DriverAssignedPayload payload = objectMapper.readValue(rawPayload, DriverAssignedPayload.class);
            rideService.handleDriverAssigned(payload);
            return ResponseEntity.ok("Processed");
        } catch (JsonProcessingException e) {
            logger.error("[RideController] Failed to parse DriverAssigned payload", e).log();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing failed");
        }
    }

    /**
     * Webhook endpoint called by Atropos when NO_DRIVER_AVAILABLE event fires from Driver Service.
     */
    @Hidden
    @PostMapping("/events/no-driver-available/webhook")
    public ResponseEntity<String> handleNoDriverAvailable(@RequestBody String rawPayload) {
        // Consistent with all other webhook handlers: log reception before processing
        logger.info("[RideController] Webhook received — NO_DRIVER_AVAILABLE").log();
        try {
            NoDriverAvailablePayload payload = objectMapper.readValue(rawPayload, NoDriverAvailablePayload.class);
            rideService.handleNoDriverAvailable(payload);
            return ResponseEntity.ok("Processed");
        } catch (JsonProcessingException e) {
            logger.error("[RideController] Failed to parse NoDriverAvailable payload", e).log();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing failed");
        }
    }

    @PatchMapping("/{rideId}/rating")
    @Operation(summary = "Rate a completed ride")
    @SandboxAuthorizedSync(action = "ride.complete", object = "1@" + RideProvider.OBJECT_TYPE + ".ridebooking.app", tenantID = "1001034")
    public ResponseEntity<RideResponseDto> rateRide(
            @PathVariable String rideId,
            @RequestBody Map<String, Object> body) {
        int rating = (int) body.getOrDefault("rating", 5);
        String feedback = (String) body.getOrDefault("feedback", "");
        return ResponseEntity.ok(rideService.rateRide(rideId, rating, feedback));
    }
}
