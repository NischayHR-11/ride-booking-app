package com.ridebooking.notificationservice.service;

import com.ridebooking.notificationservice.dto.NotificationResponseDto;
import com.ridebooking.notificationservice.entity.Notification;
import com.ridebooking.notificationservice.exception.NotificationNotFoundException;
import com.ridebooking.notificationservice.events.DriverAssignedPayload;
import com.ridebooking.notificationservice.events.RideCancelledPayload;
import com.ridebooking.notificationservice.events.RideCompletedPayload;
import com.ridebooking.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ResendEmailClient resendEmailClient;

    @Mock
    private TwilioSmsClient twilioSmsClient;

    @InjectMocks
    private NotificationService notificationService;

    private Notification sampleNotification;

    @BeforeEach
    void setUp() {
        sampleNotification = Notification.builder()
                .notificationId("notif-001")
                .userId("driver-001")
                .message("Driver driver-001 assigned to ride ride-123. Vehicle: KA01AB1234. ETA: 10 mins")
                .type("DRIVER_ASSIGNED")
                .sent(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void handleDriverAssigned_shouldSaveNotification() {
        DriverAssignedPayload payload = new DriverAssignedPayload(
                "DRIVER_ASSIGNED", "ride-123", "driver-001", "KA01AB1234", 10, "2026-05-02T10:00:00");

        notificationService.handleDriverAssigned(payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals("driver-001", saved.getUserId());
        assertEquals("DRIVER_ASSIGNED", saved.getType());
        assertTrue(saved.getMessage().contains("ride-123"));
        assertTrue(saved.getMessage().contains("driver-001"));
    }

    @Test
    void handleRideCancelled_shouldSaveNotification() {
        RideCancelledPayload payload = new RideCancelledPayload(
                "RIDE_CANCELLED", "ride-123", "rider-001", "2026-05-02T10:00:00");

        notificationService.handleRideCancelled(payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals("rider-001", saved.getUserId());
        assertEquals("RIDE_CANCELLED", saved.getType());
        assertTrue(saved.getMessage().contains("ride-123"));
    }

    @Test
    void handleRideCompleted_shouldSaveNotification() {
        RideCompletedPayload payload = new RideCompletedPayload(
                "RIDE_COMPLETED", "ride-123", "driver-001", "rider-001", "2026-05-02T10:00:00");

        notificationService.handleRideCompleted(payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals("rider-001", saved.getUserId());
        assertEquals("RIDE_COMPLETED", saved.getType());
        assertTrue(saved.getMessage().contains("ride-123"));
        assertTrue(saved.getMessage().contains("driver-001"));
    }

    @Test
    void handleDriverAssigned_shouldNotFailWhenNotifierThrows() {
        DriverAssignedPayload payload = new DriverAssignedPayload(
                "DRIVER_ASSIGNED", "ride-123", "driver-001", "KA01AB1234", 10, "2026-05-02T10:00:00");
        doThrow(new RuntimeException("send failed")).when(resendEmailClient)
                .sendEmail(anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> notificationService.handleDriverAssigned(payload));
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void getNotifications_shouldReturnMappedList() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc("driver-001"))
                .thenReturn(List.of(sampleNotification));

        List<NotificationResponseDto> result = notificationService.getNotifications("driver-001");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("notif-001", result.get(0).getNotificationId());
        assertEquals("driver-001", result.get(0).getUserId());
        assertEquals("DRIVER_ASSIGNED", result.get(0).getType());
    }

    @Test
    void getNotifications_shouldReturnEmptyListWhenNone() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc("unknown"))
                .thenReturn(List.of());

        List<NotificationResponseDto> result = notificationService.getNotifications("unknown");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void markAsRead_throwsWhenNotificationMissing() {
        when(notificationRepository.findById("missing-id")).thenReturn(Optional.empty());

        assertThrows(NotificationNotFoundException.class, () -> notificationService.markAsRead("missing-id"));
        verify(notificationRepository, never()).save(any(Notification.class));
    }
}
