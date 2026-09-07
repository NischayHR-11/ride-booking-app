package com.ridebooking.rideservice.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NoDriverAvailablePayload {
    private String eventType;
    private String rideId;
    private String reason;
    private String timestamp;
}
