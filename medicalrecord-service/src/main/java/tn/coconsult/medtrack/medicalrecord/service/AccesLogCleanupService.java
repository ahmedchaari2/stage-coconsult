package tn.coconsult.medtrack.medicalrecord.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.coconsult.medtrack.accesslog.repository.AccesLogRepository;

import java.time.LocalDateTime;

/**
 * Purge périodique des journaux d'accès, même pattern que NotificationCleanupService/
 * RefreshTokenCleanupService. Rétention plus courte que les dossiers eux-mêmes (jamais purgés) :
 * un journal d'accès sert la traçabilité récente, pas un historique clinique permanent.
 */
@Service
@RequiredArgsConstructor
public class AccesLogCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AccesLogCleanupService.class);

    private final AccesLogRepository accesLogRepository;

    @Value("${medtrack.acces-log.retention-days:30}")
    private int retentionDays;

    /** 04:00, après les autres purges (03:00 refresh tokens, 03:30 notifications) pour ne pas les faire coïncider. */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void purgeOldLogs() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        long deleted = accesLogRepository.deleteByDateHeureBefore(threshold);
        log.info("Purge des journaux d'accès avant {} ({} jours) : {} supprimé(s).", threshold, retentionDays, deleted);
    }
}
