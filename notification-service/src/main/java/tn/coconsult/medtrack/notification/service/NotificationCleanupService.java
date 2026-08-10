package tn.coconsult.medtrack.notification.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.coconsult.medtrack.notification.repository.NotificationRepository;

import java.time.LocalDateTime;

/**
 * Purge périodique des notifications lues, pour éviter une accumulation infinie (voir
 * RefreshTokenCleanupService pour le même pattern côté refresh tokens). Ne touche jamais les
 * notifications non lues, quelle que soit leur ancienneté : seule une notification déjà consultée
 * par son destinataire perd son utilité avec le temps.
 */
@Service
@RequiredArgsConstructor
public class NotificationCleanupService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupService.class);

    private final NotificationRepository notificationRepository;

    @Value("${medtrack.notification.retention-days:90}")
    private int retentionDays;

    /**
     * S'exécute tous les jours à 03:30 (heure serveur), décalé de RefreshTokenCleanupService
     * (03:00) pour ne pas faire coïncider les deux jobs.
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeReadNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        long deleted = notificationRepository.deleteByLuTrueAndLuAtBefore(threshold);
        log.info("Purge des notifications lues avant {} ({} jours) : {} supprimée(s).", threshold, retentionDays, deleted);
    }
}
