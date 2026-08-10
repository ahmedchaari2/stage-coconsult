package tn.coconsult.medtrack.user.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.coconsult.medtrack.user.repository.PasswordResetTokenRepository;

import java.time.LocalDateTime;

/**
 * Purge périodique des tokens de réinitialisation de mot de passe expirés, même pattern que
 * {@link RefreshTokenCleanupService}. Supprime tout token dont {@code expiryDate} est dépassée,
 * qu'il ait été utilisé ou non — un token expiré n'a plus aucun intérêt de conservation.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetTokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenCleanupService.class);

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * S'exécute tous les jours à 03:30 (heure serveur), décalé de 30 min par rapport à
     * {@link RefreshTokenCleanupService} pour éviter deux jobs de purge simultanés.
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.deleteByExpiryDateBefore(now);
        log.info("Purge des tokens de réinitialisation de mot de passe expirés (expiryDate < {}) terminée.", now);
    }
}
