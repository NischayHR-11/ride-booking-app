package com.ridebooking.notificationservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridebooking.notificationservice.dto.NotificationResponseDto;
import com.ridebooking.notificationservice.events.DriverAssignedPayload;
import com.ridebooking.notificationservice.events.RideCancelledPayload;
import com.ridebooking.notificationservice.events.RideCompletedPayload;
import com.ridebooking.notificationservice.service.NotificationService;
import in.zeta.spectra.capture.SpectraLogger;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import olympus.trace.OlympusSpectra;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Consumer;

/*
 * Design Patterns used in this class:
 *
 * 1. DEPENDENCY INJECTION (Constructor Injection via @RequiredArgsConstructor)
 *    — Spring injects NotificationService and ObjectMapper; no manual instantiation needed.
 *
 * 2. TEMPLATE METHOD (private processWebhook helper)
 *    — All Atropos webhook endpoints share the same structure:
 *      log → deserialize → delegate → respond.
 *    — processWebhook() centralises this skeleton, eliminating copy-paste
 *      across the three event handlers and keeping each handler a one-liner.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification API", description = "View notifications and webhook consumers for Atropos events")
public class NotificationController {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(NotificationController.class);
    private static final String WEBHOOK_ERROR_RESPONSE = "Failed";

    // [DI] Injected by Spring via constructor (Lombok @RequiredArgsConstructor)
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @GetMapping
    @Operation(summary = "Get all notifications")
    public ResponseEntity<List<NotificationResponseDto>> getAllNotifications() {
        return ResponseEntity.ok(notificationService.getAllNotifications());
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get notifications for a user")
    public ResponseEntity<List<NotificationResponseDto>> getNotifications(@PathVariable String userId) {
        return ResponseEntity.ok(notificationService.getNotifications(userId));
    }

    /** Webhook endpoint called by Atropos when DRIVER_ASSIGNED event fires. */
    @Hidden
    @PostMapping("/events/driver-assigned/webhook")
    public ResponseEntity<String> handleDriverAssigned(@RequestBody String rawPayload) {
        // [Template Method] Delegates to processWebhook — only the type and handler differ
        return processWebhook(rawPayload, "DRIVER_ASSIGNED", DriverAssignedPayload.class,
                notificationService::handleDriverAssigned);
    }

    /** Webhook endpoint called by Atropos when RIDE_CANCELLED event fires. */
    @Hidden
    @PostMapping("/events/ride-cancelled/webhook")
    public ResponseEntity<String> handleRideCancelled(@RequestBody String rawPayload) {
        return processWebhook(rawPayload, "RIDE_CANCELLED", RideCancelledPayload.class,
                notificationService::handleRideCancelled);
    }

    /** Webhook endpoint called by Atropos when RIDE_COMPLETED event fires. */
    @Hidden
    @PostMapping("/events/ride-completed/webhook")
    public ResponseEntity<String> handleRideCompleted(@RequestBody String rawPayload) {
        return processWebhook(rawPayload, "RIDE_COMPLETED", RideCompletedPayload.class,
                notificationService::handleRideCompleted);
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<NotificationResponseDto> markAsRead(@PathVariable String notificationId) {
        return ResponseEntity.ok(notificationService.markAsRead(notificationId));
    }

    // -------------------------------------------------------------------------
    // [Template Method Pattern — START]
    // Encapsulates the invariant webhook processing skeleton:
    //   1. Log reception
    //   2. Deserialise raw JSON into the typed payload
    //   3. Delegate to the service handler
    //   4. Return a uniform success / error response
    // Each caller only supplies what varies: the event name, the payload type,
    // and the service method reference.
    // -------------------------------------------------------------------------
    private <T> ResponseEntity<String> processWebhook(
            String rawPayload, String eventType, Class<T> payloadClass, Consumer<T> handler) {
        logger.info("[NotificationController] Webhook received").attr("event", eventType).log();
        try {
            T payload = objectMapper.readValue(rawPayload, payloadClass);
            handler.accept(payload);
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            logger.error("[NotificationController] Failed to process webhook", e).attr("event", eventType).log();
            return ResponseEntity.internalServerError().body(WEBHOOK_ERROR_RESPONSE);
        }
    }
    // [Template Method Pattern — END]
}
