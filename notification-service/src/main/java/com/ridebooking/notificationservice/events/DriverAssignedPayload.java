package com.ridebooking.notificationservice.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DriverAssignedPayload {
    private String eventType;
    private String rideId;
    private String driverId;
    private String vehicleNumber;
    private int eta;
    private String timestamp;
}
