package tn.coconsult.medtrack.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.coconsult.medtrack.common.dto.PageDTO;
import tn.coconsult.medtrack.notification.dto.NotificationResponse;
import tn.coconsult.medtrack.notification.service.NotificationService;

/**
 * Notifications de l'utilisateur connecté uniquement (ADMIN et MEDECIN chacun leurs propres
 * notifications, jamais celles d'un autre) : voir NotificationService, l'appartenance est
 * vérifiée en service, pas seulement par le filtre de requête.
 * <p>
 * Le déclenchement d'une notification de rendez-vous ne passe plus par un endpoint HTTP interne
 * (voir AppointmentEventListener) : appointment-service publie sur RabbitMQ plutôt que d'appeler
 * ce service directement.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping({"", "/"})
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<PageDTO<NotificationResponse>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(notificationService.search(page, size));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<Long> unreadCount() {
        return ResponseEntity.ok(notificationService.unreadCount());
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.noContent().build();
    }
}
