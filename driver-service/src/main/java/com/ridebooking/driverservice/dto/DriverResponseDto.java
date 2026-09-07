package com.ridebooking.driverservice.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverResponseDto {
    private String driverId;
    private String name;
    private String phone;
    private String vehicleNumber;
    private String vehicleType;
    private double latitude;
    private double longitude;
    private boolean available;
    private double rating;
    private LocalDateTime createdAt;
}
