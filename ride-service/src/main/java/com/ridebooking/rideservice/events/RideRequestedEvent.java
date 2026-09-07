package com.ridebooking.rideservice.events;

import lombok.*;
import java.time.Instant;

/*
 * Design Patterns:
 *   BUILDER  — Lombok @Builder generates a fluent builder for safe, readable construction.
 *   STATIC FACTORY METHOD — RideRequestedEvent.of(...) is the preferred construction
 *     entry-point: it sets all fields (including eventType and timestamp) in one call,
 *     preventing partially-initialised objects from escaping into the codebase.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideRequestedEvent {
    @Builder.Default
    private String eventType = "RIDE_REQUESTED";
    private String rideId;
    private String riderId;
    private String pickupLocation;
    private String dropLocation;
    private double pickupLat;
    private double pickupLng;
    private String timestamp;

    public static RideRequestedEvent of(String rideId, String riderId, String pickup, String drop,
                                        double pickupLat, double pickupLng) {
        return RideRequestedEvent.builder()
                .eventType("RIDE_REQUESTED")
                .rideId(rideId)
                .riderId(riderId)
                .pickupLocation(pickup)
                .dropLocation(drop)
                .pickupLat(pickupLat)
                .pickupLng(pickupLng)
                .timestamp(Instant.now().toString())
                .build();
    }
}
