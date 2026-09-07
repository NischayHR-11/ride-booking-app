package com.ridebooking.rideservice.controller;

import com.ridebooking.rideservice.dto.RiderRequestDto;
import com.ridebooking.rideservice.dto.RiderResponseDto;
import com.ridebooking.rideservice.provider.UserProvider;
import com.ridebooking.rideservice.service.RiderService;
import in.zeta.springframework.boot.commons.authorization.sandboxAccessControl.SandboxAuthorizedSync;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for rider management endpoints.
 * Provides APIs for registering riders and retrieving rider details.
 * All endpoints are authenticated and require valid request payloads.
 */
@RestController
@RequestMapping("/api/riders")
@RequiredArgsConstructor
@Tag(name = "Rider API", description = "Register and manage riders")
public class RiderController {

    private final RiderService riderService;

    /**
     * Register a new rider with the provided details.
     *
     * @param request the rider registration request containing name, phone, and email
     * @return ResponseEntity containing the registered rider details with HTTP 201 Created status
     * @throws ResponseStatusException if phone or email is already registered
     */
    @PostMapping
    @Operation(summary = "Register a new rider")
    @SandboxAuthorizedSync(action = "rider.create", object = "1@" + UserProvider.OBJECT_TYPE + ".ridebooking.app", tenantID = "1001034")
    public ResponseEntity<RiderResponseDto> registerRider(@Valid @RequestBody RiderRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(riderService.registerRider(request));
    }

    /**
     * Get all registered riders.
     *
     * @return ResponseEntity containing list of all riders with HTTP 200 OK status
     */
    @GetMapping
    @Operation(summary = "Get all riders")
    @SandboxAuthorizedSync(action = "rider.view", object = "1@" + UserProvider.OBJECT_TYPE + ".ridebooking.app", tenantID = "1001034")
    public ResponseEntity<List<RiderResponseDto>> getAllRiders() {
        return ResponseEntity.ok(riderService.getAllRiders());
    }

    @GetMapping("/{riderId}")
    @Operation(summary = "Get rider details by ID")
    @SandboxAuthorizedSync(action = "rider.view", object = "1@" + UserProvider.OBJECT_TYPE + ".ridebooking.app", tenantID = "1001034")
    public ResponseEntity<RiderResponseDto> getRider(@PathVariable String riderId) {
        return ResponseEntity.ok(riderService.getRider(riderId));
    }
}
