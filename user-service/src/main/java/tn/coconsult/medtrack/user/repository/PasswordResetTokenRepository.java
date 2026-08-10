package tn.coconsult.medtrack.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.coconsult.medtrack.user.model.PasswordResetToken;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    void deleteByExpiryDateBefore(LocalDateTime expiryDate);
}
