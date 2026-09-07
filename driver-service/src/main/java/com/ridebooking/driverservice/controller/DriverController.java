package com.ridebooking.driverservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridebooking.driverservice.dto.DriverRegisterDto;
import com.ridebooking.driverservice.dto.DriverResponseDto;
import com.ridebooking.driverservice.dto.LocationUpdateDto;
import com.ridebooking.driverservice.events.RideRequestedEventPayload;
import com.ridebooking.driverservice.provider.DriverProvider;
import com.ridebooking.driverservice.service.DriverService;
import in.zeta.spectra.capture.SpectraLogger;
import in.zeta.springframework.boot.commons.authorization.sandboxAccessControl.SandboxAuthorizedSync;
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
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@Tag(name = "Driver API", description = "Manage drivers, availability, location, and ride assignment")
public class DriverController {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(DriverController.class);

    private final DriverService driverService;
    private final ObjectMapper objectMapper;

    @PostMapping
    @Operation(summary = "Register a new driver")
    @SandboxAuthorizedSync(action = "driver.create", object = "1@" + DriverProvider.OBJECT_TYPE + ".ridebooking.app", tenantID = "1001034")
    public ResponseEntity<DriverResponseDto> registerDriver(@Valid @RequestBody DriverRegisterDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(driverService.registerDriver(dto));
    }

    @GetMapping("/{driverId}")
    @Operation(summary = "Get driver details")
    @SandboxAuthorizedSync(action = "driver.view", object = "1@" + DriverProvider.OBJECT_TYPE + ".ridebooking.app", tenantID = "1001034")
    public ResponseEntity<DriverResponseDto> getDriver(@PathVariable String driverId) {
        return ResponseEntity.ok(driverService.getDriver(driverId));
    }

    @PatchMapping("/{driverId}/availability")
    @Operation(summary = "Update driver availability")
    @SandboxAuthorizedSync(action = "driver.availability", object = "1@" + DriverProvider.OBJECT_TYPE + ".ridebooking.app", tenantID = "1001034")
    public ResponseEntity<DriverResponseDto> updateAvailability(
            @PathVariable String driverId,
            @RequestBody Map<String, Boolean> body) {
        boolean available = body.getOrDefault("available", true);
        return ResponseEntity.ok(driverService.updateAvailability(driverId, available));
    }

    @PatchMapping("/{driverId}/location")
    @Operation(summary = "Update driver GPS location")
    @SandboxAuthorizedSync(action = "driver.update.location", object = "1@" + DriverProvider.OBJECT_TYPE + ".ridebooking.app", tenantID = "1001034")
    public ResponseEntity<DriverResponseDto> updateLocation(
            @PathVariable String driverId,
            @RequestBody LocationUpdateDto dto) {
        return ResponseEntity.ok(driverService.updateLocation(driverId, dto));
    }

    /**
     * Webhook endpoint called by Atropos when a RIDE_REQUESTED event is fired.
     * Atropos delivers the event payload to this URL.
     */
    @Hidden
    @PostMapping("/events/ride-requested/webhook")
    public ResponseEntity<String> handleRideRequestedWebhook(@RequestBody String rawPayload) {
        logger.info("[DriverController] Webhook received — RIDE_REQUESTED").log();
        try {
            RideRequestedEventPayload payload = objectMapper.readValue(rawPayload, RideRequestedEventPayload.class);
            driverService.handleRideRequested(payload);
            return ResponseEntity.ok("Processed");
        } catch (Exception e) {
            logger.error("[DriverController] Failed to process RIDE_REQUESTED webhook", e).log();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing failed");
        }
    }

    @GetMapping
    @Operation(summary = "Get all available drivers")
    @SandboxAuthorizedSync(action = "driver.view", object = "1@" + DriverProvider.OBJECT_TYPE + ".ridebooking.app", tenantID = "1001034")
    public ResponseEntity<List<DriverResponseDto>> getAvailableDrivers() {
        return ResponseEntity.ok(driverService.getAvailableDrivers());
    }

    @GetMapping("/all")
    @Operation(summary = "Get all drivers")
    @SandboxAuthorizedSync(action = "driver.view", object = "1@" + DriverProvider.OBJECT_TYPE + ".ridebooking.app", tenantID = "1001034")
    public ResponseEntity<List<DriverResponseDto>> getAllDrivers() {
        return ResponseEntity.ok(driverService.getAllDrivers());
    }

    @GetMapping("/{driverId}/stats")
    @Operation(summary = "Get driver statistics")
    @SandboxAuthorizedSync(action = "driver.view", object = "1@" + DriverProvider.OBJECT_TYPE + ".ridebooking.app", tenantID = "1001034")
    public ResponseEntity<Map<String, Object>> getDriverStats(@PathVariable String driverId) {
        return ResponseEntity.ok(driverService.getDriverStats(driverId));
    }
}
