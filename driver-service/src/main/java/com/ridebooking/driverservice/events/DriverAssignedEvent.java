package com.ridebooking.driverservice.events;

import lombok.*;
import java.time.Instant;

/*
 * Design Patterns:
 *   BUILDER  — Lombok @Builder.
 *   STATIC FACTORY METHOD — DriverAssignedEvent.of(...) is the preferred entry-point;
 *     it guarantees eventType and timestamp are always populated correctly.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverAssignedEvent {
    private String eventType;
    private String rideId;
    private String driverId;
    private String vehicleNumber;
    private int eta;
    private String timestamp;

    public static DriverAssignedEvent of(String rideId, String driverId, String vehicleNumber, int eta) {
        return DriverAssignedEvent.builder()
                .eventType("DRIVER_ASSIGNED")
                .rideId(rideId)
                .driverId(driverId)
                .vehicleNumber(vehicleNumber)
                .eta(eta)
                .timestamp(Instant.now().toString())
                .build();
    }
}
