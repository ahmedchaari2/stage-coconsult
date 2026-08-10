package tn.coconsult.medtrack.user.service;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.user.config.JwtService;
import tn.coconsult.medtrack.user.dto.LoginRequest;
import tn.coconsult.medtrack.user.dto.LoginResponse;
import tn.coconsult.medtrack.user.dto.LoginResult;
import tn.coconsult.medtrack.user.model.RefreshToken;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.RefreshTokenRepository;
import tn.coconsult.medtrack.user.repository.UserRepository;
import tn.coconsult.medtrack.user.util.EmailNormalizer;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiration-days}")
    private long refreshTokenExpirationDays;

    /** Authentifie l'utilisateur et délivre un access token + un refresh token (persisté en base pour révocation/rotation). */
    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByEmail(EmailNormalizer.normalize(request.getEmail()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!passwordMatches) {
            throw new BadCredentialsException("Invalid credentials");
        }

        // Sécurité : bloquer les comptes désactivés AVANT de générer le JWT.
        if (Boolean.FALSE.equals(user.getActif())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Ce compte a été désactivé. Contactez votre administrateur.");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        persistRefreshToken(jwtService.extractJti(refreshToken), user);

        // Le body ne contient QUE les infos utilisateur : les deux tokens sont posés
        // en cookies httpOnly par le controller (aucun token dans le corps de réponse).
        LoginResponse loginResponse = buildLoginResponse(user);
        return new LoginResult(loginResponse, accessToken, refreshToken);
    }

    /**
     * Renouvelle la paire de tokens à partir d'un refresh token valide (présenté via cookie).
     * Rotation : l'ancien est révoqué immédiatement, un nouveau est émis et persisté.
     */
    public LoginResult refreshTokens(String refreshToken) {
        String jti;
        try {
            jti = jwtService.extractJti(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalide");
        }

        boolean valid = jwtService.isTokenValid(refreshToken);
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalide ou expiré");
        }

        java.util.Optional<RefreshToken> opt = refreshTokenRepository.findByToken(jti);
        RefreshToken stored = opt.orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token inconnu"));

        if (stored.getRevoked()) {
            // Token déjà révoqué re-présenté : possible vol, on révoque toute la famille
            User compromisedUser = stored.getUser();
            log.warn("Refresh token déjà révoqué re-présenté (possible vol) — "
                    + "révocation de toute la famille pour l'utilisateur {}", compromisedUser.getEmail());
            revokeAllForUser(compromisedUser);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token révoqué");
        }
        if (stored.getExpiryDate().isBefore(LocalDateTime.now())) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expiré");
        }

        User user = stored.getUser();
        if (Boolean.FALSE.equals(user.getActif())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Ce compte a été désactivé. Contactez votre administrateur.");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);
        persistRefreshToken(jwtService.extractJti(newRefreshToken), user);

        LoginResponse loginResponse = buildLoginResponse(user);
        return new LoginResult(loginResponse, newAccessToken, newRefreshToken);
    }

    /**
     * Révoque le refresh token (best-effort) à la déconnexion. Un token illisible ou inconnu
     * ne provoque pas d'erreur : la déconnexion reste un succès côté client.
     */
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            String jti = jwtService.extractJti(refreshToken);
            refreshTokenRepository.findByToken(jti).ifPresent(stored -> {
                stored.setRevoked(true);
                refreshTokenRepository.save(stored);
            });
        } catch (JwtException | IllegalArgumentException e) {
            // Token illisible : rien à révoquer en base.
        }
    }

    private void persistRefreshToken(String jti, User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(jti);
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(LocalDateTime.now().plusDays(refreshTokenExpirationDays));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);
    }

    /**
     * Révoque tous les refresh tokens d'un utilisateur (révocation de famille), en cas de
     * réutilisation suspecte d'un token ou de réinitialisation de mot de passe. Package-private
     * car aussi appelé par {@link PasswordResetService}.
     */
    void revokeAllForUser(User user) {
        List<RefreshToken> all = refreshTokenRepository.findByUser(user);
        if (all.isEmpty()) {
            return;
        }
        all.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(all);
    }

    /**
     * Révoque tous les refresh tokens d'un utilisateur SAUF celui dont le jti correspond à
     * {@code currentJti} (session courante). Utilisé par le changement de mot de passe :
     * l'utilisateur reste connecté sur l'appareil courant, déconnecté partout ailleurs.
     * Si {@code currentJti} est {@code null} (cookie refreshToken absent/illisible), aucun
     * token ne peut être identifié comme "courant" : tous sont révoqués (repli sûr).
     */
    void revokeAllForUserExceptCurrent(User user, String currentJti) {
        List<RefreshToken> all = refreshTokenRepository.findByUser(user);
        List<RefreshToken> toRevoke = all.stream()
                .filter(token -> !token.getToken().equals(currentJti))
                .toList();
        if (toRevoke.isEmpty()) {
            return;
        }
        toRevoke.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(toRevoke);
    }

    private LoginResponse buildLoginResponse(User user) {
        return new LoginResponse(
                user.getId(),
                user.getEmail(),
                user.getNom(),
                user.getPrenom(),
                user.getRole(),
                user.getActif(),
                user.getPhotoUrl(),
                user.getSpecialite(),
                user.getNumeroOrdre(),
                user.getTelephone(),
                user.getCreatedAt()
        );
    }

}
