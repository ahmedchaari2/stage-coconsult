package tn.coconsult.medtrack.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.coconsult.medtrack.user.model.RefreshToken;
import tn.coconsult.medtrack.user.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByUser(User user);

    void deleteByUser(User user);

    void deleteByExpiryDateBefore(LocalDateTime expiryDate);
}
