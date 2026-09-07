package com.ridebooking.rideservice.events;

import lombok.*;
import java.time.Instant;

/*
 * Design Patterns:
 *   BUILDER  — Lombok @Builder.
 *   STATIC FACTORY METHOD — RideCompletedEvent.of(...) is the preferred entry-point.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideCompletedEvent {
    private String eventType;
    private String rideId;
    private String driverId;
    private String riderId;
    private String timestamp;

    public static RideCompletedEvent of(String rideId, String driverId, String riderId) {
        return RideCompletedEvent.builder()
                .eventType("RIDE_COMPLETED")
                .rideId(rideId)
                .driverId(driverId)
                .riderId(riderId)
                .timestamp(Instant.now().toString())
                .build();
    }
}
