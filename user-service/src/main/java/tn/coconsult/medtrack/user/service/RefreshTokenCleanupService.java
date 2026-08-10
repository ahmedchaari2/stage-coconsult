package tn.coconsult.medtrack.user.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.coconsult.medtrack.user.repository.RefreshTokenRepository;

import java.time.LocalDateTime;

/**
 * Purge périodique des refresh tokens expirés. Chaque login/refresh insère une nouvelle ligne
 * (rotation) jamais supprimée par le flux normal, donc la table grossirait indéfiniment sinon.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupService.class);

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * S'exécute tous les jours à 03:00 (heure serveur). Supprime les tokens expirés.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.deleteByExpiryDateBefore(now);
        log.info("Purge des refresh tokens expirés (expiryDate < {}) terminée.", now);
    }
}
