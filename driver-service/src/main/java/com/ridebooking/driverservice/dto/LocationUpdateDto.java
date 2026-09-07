package com.ridebooking.driverservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LocationUpdateDto {
    private double latitude;
    private double longitude;
}
