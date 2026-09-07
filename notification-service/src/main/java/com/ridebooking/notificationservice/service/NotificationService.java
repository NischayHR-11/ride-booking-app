package com.ridebooking.notificationservice.service;

import com.ridebooking.notificationservice.dto.NotificationResponseDto;
import com.ridebooking.notificationservice.entity.Notification;
import com.ridebooking.notificationservice.events.DriverAssignedPayload;
import com.ridebooking.notificationservice.events.RideCancelledPayload;
import com.ridebooking.notificationservice.events.RideCompletedPayload;
import com.ridebooking.notificationservice.exception.NotificationNotFoundException;
import com.ridebooking.notificationservice.repository.NotificationRepository;
import in.zeta.spectra.capture.SpectraLogger;
import lombok.RequiredArgsConstructor;
import olympus.trace.OlympusSpectra;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/*
 * Design Patterns used in this service:
 *
 * 1. DEPENDENCY INJECTION (Constructor Injection via @RequiredArgsConstructor)
 *    — NotificationRepository is injected by Spring.
 *
 * 2. REPOSITORY (NotificationRepository extends JpaRepository)
 *    — Persistence is accessed through the repository abstraction.
 *
 * 3. BUILDER (Lombok @Builder on Notification entity)
 *    — saveNotification() uses the builder for safe, readable entity construction.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(NotificationService.class);
    private static final String RIDE_ID_ATTR = "rideId";

    private final NotificationRepository notificationRepository;
    private final ResendEmailClient resendEmailClient;
    private final TwilioSmsClient twilioSmsClient;

    public void handleDriverAssigned(DriverAssignedPayload payload) {
        String smsMessage = String.format("Driver %s assigned to ride %s. Vehicle: %s. ETA: %d mins",
                payload.getDriverId(), payload.getRideId(), payload.getVehicleNumber(), payload.getEta());
        String htmlBody = buildDriverAssignedHtml(payload.getRideId(), payload.getDriverId(),
                payload.getVehicleNumber(), payload.getEta());
        saveNotification(payload.getDriverId(), smsMessage, "DRIVER_ASSIGNED");
        safeNotify("email-driver-assigned", () -> resendEmailClient.sendEmail(
                payload.getDriverId(),
                "\uD83D\uDE97 Driver Assigned — Ride " + payload.getRideId(),
                htmlBody));
        safeNotify("sms-driver-assigned", () -> twilioSmsClient.sendSms(payload.getDriverId(), smsMessage));
        logger.info("[NotificationService] Notification sent — DRIVER_ASSIGNED")
                .attr(RIDE_ID_ATTR, payload.getRideId())
                .attr("driverId", payload.getDriverId())
                .log();
    }

    public void handleRideCancelled(RideCancelledPayload payload) {
        String smsMessage = String.format("Ride %s has been cancelled.", payload.getRideId());
        String htmlBody = buildRideCancelledHtml(payload.getRideId(), payload.getRiderId());
        saveNotification(payload.getRiderId(), smsMessage, "RIDE_CANCELLED");
        safeNotify("email-ride-cancelled", () -> resendEmailClient.sendEmail(
                payload.getRiderId(),
                "\u26A0\uFE0F Ride Cancelled — " + payload.getRideId(),
                htmlBody));
        safeNotify("sms-ride-cancelled", () -> twilioSmsClient.sendSms(payload.getRiderId(), smsMessage));
        logger.info("[NotificationService] Notification sent — RIDE_CANCELLED")
                .attr(RIDE_ID_ATTR, payload.getRideId())
                .attr("riderId", payload.getRiderId())
                .log();
    }

    public void handleRideCompleted(RideCompletedPayload payload) {
        String smsMessage = String.format("Ride %s completed by driver %s. Thank you for riding with us!", payload.getRideId(), payload.getDriverId());
        String htmlBody = buildRideCompletedHtml(payload.getRideId(), payload.getDriverId(), payload.getRiderId());
        saveNotification(payload.getRiderId(), smsMessage, "RIDE_COMPLETED");
        safeNotify("email-ride-completed", () -> resendEmailClient.sendEmail(
                payload.getRiderId(),
                "\u2705 Ride Completed — " + payload.getRideId(),
                htmlBody));
        safeNotify("sms-ride-completed", () -> twilioSmsClient.sendSms(payload.getRiderId(), smsMessage));
        logger.info("[NotificationService] Notification sent — RIDE_COMPLETED")
                .attr(RIDE_ID_ATTR, payload.getRideId())
                .attr("riderId", payload.getRiderId())
                .log();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getAllNotifications() {
        return notificationRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getNotifications(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private void saveNotification(String userId, String message, String type) {
        Notification notification = Notification.builder()
                .userId(userId)
                .message(message)
                .type(type)
                .sent(true)
                .build();
        notificationRepository.save(notification);
    }

    public NotificationResponseDto markAsRead(String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found: " + notificationId));

        notification.setRead(true);
        notificationRepository.save(notification);

        logger.info("[NotificationService] Notification marked as read")
                .attr("notificationId", notificationId)
                .log();

        return mapToResponse(notification);
    }

    private NotificationResponseDto mapToResponse(Notification n) {
        return NotificationResponseDto.builder()
                .notificationId(n.getNotificationId())
                .userId(n.getUserId())
                .message(n.getMessage())
                .type(n.getType())
                .sent(n.isSent())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }

    // ─── HTML Email Builders ──────────────────────────────────────────────────

    private static String emailWrapper(String accentColor, String headerIcon, String headerTitle, String bodyContent) {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>"
                + "<body style=\"margin:0;padding:0;background-color:#f0f4f8;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f0f4f8;padding:40px 16px;\">"
                + "<tr><td align=\"center\">"
                + "<table width=\"560\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#ffffff;border-radius:16px;"
                + "overflow:hidden;box-shadow:0 6px 24px rgba(0,0,0,0.09);\">"
                + "<tr><td style=\"background:" + accentColor + ";padding:36px 40px;text-align:center;\">"
                + "<div style=\"font-size:40px;margin-bottom:10px;\">" + headerIcon + "</div>"
                + "<h1 style=\"color:#ffffff;margin:0;font-size:22px;font-weight:700;letter-spacing:0.5px;\">"
                + headerTitle + "</h1>"
                + "<p style=\"color:rgba(255,255,255,0.75);margin:6px 0 0;font-size:12px;letter-spacing:1px;\">RIDEBOOKING PLATFORM</p>"
                + "</td></tr>"
                + "<tr><td style=\"padding:36px 40px;\">" + bodyContent + "</td></tr>"
                + "<tr><td style=\"background:#f7fafc;padding:20px 40px;border-top:1px solid #e2e8f0;text-align:center;\">"
                + "<p style=\"color:#a0aec0;margin:0;font-size:11px;\">&#169; 2026 RideBooking &bull; You received this because you have an active account.</p>"
                + "</td></tr></table></td></tr></table></body></html>";
    }

    private static String infoCard(String borderColor, String label, String value) {
        return "<div style=\"background:#f7fafc;border-left:4px solid " + borderColor
                + ";border-radius:8px;padding:14px 16px;margin-bottom:12px;\">"
                + "<p style=\"color:#718096;margin:0 0 4px;font-size:10px;text-transform:uppercase;"
                + "letter-spacing:1px;font-weight:700;\">" + label + "</p>"
                + "<p style=\"color:#1a202c;margin:0;font-size:15px;font-weight:700;\">" + value + "</p>"
                + "</div>";
    }

    private static String buildDriverAssignedHtml(String rideId, String driverId, String vehicleNumber, int eta) {
        String body = "<h2 style=\"color:#1a202c;margin:0 0 6px;font-size:20px;font-weight:700;\">"
                + "Great news! Your driver is on the way &#127775;</h2>"
                + "<p style=\"color:#4a5568;margin:0 0 24px;font-size:14px;line-height:1.7;\">"
                + "A driver has been successfully matched and assigned to your ride. "
                + "Please be ready at your pickup point.</p>"
                + infoCard("#4299e1", "Ride ID", rideId)
                + infoCard("#48bb78", "Driver ID", driverId)
                + infoCard("#ed8936", "Vehicle Number", vehicleNumber)
                + infoCard("#9f7aea", "Estimated Arrival", eta + " minutes")
                + "<p style=\"color:#718096;margin:24px 0 0;font-size:13px;line-height:1.6;\">"
                + "Please have your ride details ready. Your driver will meet you at the pickup location.</p>";
        return emailWrapper(
                "linear-gradient(135deg,#1a1a2e 0%,#16213e 50%,#0f3460 100%)",
                "\uD83D\uDE97", "Driver Assigned", body);
    }

    private static String buildRideCompletedHtml(String rideId, String driverId, String riderId) {
        String body = "<h2 style=\"color:#1a202c;margin:0 0 6px;font-size:20px;font-weight:700;\">"
                + "You have reached your destination! &#127881;</h2>"
                + "<p style=\"color:#4a5568;margin:0 0 24px;font-size:14px;line-height:1.7;\">"
                + "Your ride has been completed successfully. Thank you for choosing RideBooking &mdash; "
                + "we hope you had a great experience!</p>"
                + infoCard("#4299e1", "Ride ID", rideId)
                + infoCard("#48bb78", "Driver ID", driverId)
                + infoCard("#9f7aea", "Rider ID", riderId)
                + "<div style=\"background:#ebf8ee;border:1px solid #c6f6d5;border-radius:8px;padding:16px;margin-top:20px;\">"
                + "<p style=\"color:#276749;margin:0;font-size:13px;line-height:1.6;\">&#11088; "
                + "<strong>Rate your experience!</strong> Your feedback helps us improve and reward great drivers. "
                + "Open the app to leave a rating.</p></div>";
        return emailWrapper(
                "linear-gradient(135deg,#134e5e 0%,#1a6b4a 100%)",
                "\u2705", "Ride Completed", body);
    }

    private static String buildRideCancelledHtml(String rideId, String riderId) {
        String body = "<h2 style=\"color:#1a202c;margin:0 0 6px;font-size:20px;font-weight:700;\">"
                + "Your ride has been cancelled</h2>"
                + "<p style=\"color:#4a5568;margin:0 0 24px;font-size:14px;line-height:1.7;\">"
                + "We are sorry to inform you that this ride was cancelled. "
                + "You can book a new ride anytime through the app.</p>"
                + infoCard("#4299e1", "Ride ID", rideId)
                + infoCard("#fc8181", "Rider ID", riderId)
                + "<div style=\"background:#fff5f5;border:1px solid #fed7d7;border-radius:8px;padding:16px;margin-top:20px;\">"
                + "<p style=\"color:#c53030;margin:0;font-size:13px;line-height:1.6;\">&#128683; "
                + "If you did not request this cancellation or believe this is an error, "
                + "please contact our support team immediately.</p></div>";
        return emailWrapper(
                "linear-gradient(135deg,#744210 0%,#c05621 100%)",
                "\u26A0\uFE0F", "Ride Cancelled", body);
    }

    private void safeNotify(String action, Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception ex) {
            logger.error("[NotificationService] Notification channel failed")
                    .attr("action", action)
                    .attr("error", ex.getMessage())
                    .log();
        }
    }
}
