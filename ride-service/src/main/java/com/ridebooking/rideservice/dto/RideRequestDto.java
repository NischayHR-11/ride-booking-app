package com.ridebooking.rideservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideRequestDto {

    @NotBlank(message = "Rider ID is required")
    private String riderId;

    @NotBlank(message = "Pickup location is required")
    private String pickupLocation;

    @NotBlank(message = "Drop location is required")
    private String dropLocation;

    // Pickup coordinates (default 0.0 — simulates no geocoding; set real values to enable proximity matching)
    private double pickupLat;
    private double pickupLng;
}
