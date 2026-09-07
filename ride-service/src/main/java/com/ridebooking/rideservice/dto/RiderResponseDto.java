package com.ridebooking.rideservice.dto;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for rider response data.
 * Contains complete rider information including ID, personal details, role, and creation timestamp.
 * Returned by API endpoints after rider operations such as registration or retrieval.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiderResponseDto {
    private String riderId;
    private String name;
    private String phone;
    private String email;
    private String role;
    private LocalDateTime createdAt;
}
