package com.ridebooking.notificationservice.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RideCancelledPayload {
    private String eventType;
    private String rideId;
    private String riderId;
    private String timestamp;
}
