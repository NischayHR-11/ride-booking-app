package com.ridebooking.driverservice.events;

import lombok.*;
import java.time.Instant;

/*
 * Design Patterns:
 *   BUILDER  — Lombok @Builder.
 *   STATIC FACTORY METHOD — NoDriverAvailableEvent.of(...) is the preferred entry-point.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoDriverAvailableEvent {
    private String eventType;
    private String rideId;
    private String reason;
    private String timestamp;

    public static NoDriverAvailableEvent of(String rideId, String reason) {
        return NoDriverAvailableEvent.builder()
                .eventType("NO_DRIVER_AVAILABLE")
                .rideId(rideId)
                .reason(reason)
                .timestamp(Instant.now().toString())
                .build();
    }
}
