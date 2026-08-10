package tn.coconsult.medtrack.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private Long appointmentId;
    private LocalDateTime dateHeure;
    private boolean lu;
    private LocalDateTime luAt;
    private LocalDateTime createdAt;
    private AppointmentNotificationData data;
}
