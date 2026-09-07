package com.ridebooking.rideservice.events;

import lombok.*;
import java.time.Instant;

/*
 * Design Patterns:
 *   BUILDER  — Lombok @Builder.
 *   STATIC FACTORY METHOD — RideCancelledEvent.of(...) is the preferred entry-point.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideCancelledEvent {
    private String eventType;
    private String rideId;
    private String riderId;
    private String timestamp;

    public static RideCancelledEvent of(String rideId, String riderId) {
        return RideCancelledEvent.builder()
                .eventType("RIDE_CANCELLED")
                .rideId(rideId)
                .riderId(riderId)
                .timestamp(Instant.now().toString())
                .build();
    }
}
