package com.ridebooking.notificationservice.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponseDto {
    private String notificationId;
    private String userId;
    private String message;
    private String type;
    private boolean sent;
    private boolean read;
    private LocalDateTime createdAt;
}
