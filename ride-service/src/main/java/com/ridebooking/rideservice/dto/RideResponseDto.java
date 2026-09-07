package com.ridebooking.rideservice.dto;

import com.ridebooking.rideservice.entity.RideStatus;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideResponseDto {
    private String rideId;
    private String riderId;
    private String riderName;
    private String driverId;
    private String pickupLocation;
    private String dropLocation;
    private double pickupLat;
    private double pickupLng;
    private Double estimatedCost;
    private RideStatus status;
    private Integer rating;
    private String feedback;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
