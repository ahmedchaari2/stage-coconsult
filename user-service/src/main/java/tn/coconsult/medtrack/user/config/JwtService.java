package tn.coconsult.medtrack.user.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.coconsult.medtrack.user.model.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration-minutes}")
    private long accessTokenExpirationMinutes;

    @Value("${jwt.refresh-token-expiration-days}")
    private long refreshTokenExpirationDays;

    /**
     * Génère un JWT d'accès (courte durée). Contient les claims métier (id, email, nom, prénom, rôle)
     * utilisés par le filtre pour peupler le contexte de sécurité. Non révocable avant expiration.
     */
    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("email", user.getEmail());
        claims.put("nom", user.getNom());
        claims.put("prenom", user.getPrenom());
        claims.put("role", user.getRole().name());

        Instant now = Instant.now();
        return Jwts.builder()
                .claims(claims)
                .subject(user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenExpirationMinutes, ChronoUnit.MINUTES)))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Génère un JWT de rafraîchissement (longue durée) porteur d'un claim "jti" (UUID).
     * Le jti est persisté en base (entité RefreshToken) afin de permettre la révocation
     * et la rotation. La signature utilise la même clé que le token d'accès.
     */
    public String generateRefreshToken(User user) {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        return Jwts.builder()
                .id(jti)
                .subject(user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTokenExpirationDays, ChronoUnit.DAYS)))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        Object id = claims.get("id");
        if (id instanceof Number) {
            return ((Number) id).longValue();
        }
        return null;
    }

    public String extractNom(String token) {
        return extractAllClaims(token).get("nom", String.class);
    }

    public String extractPrenom(String token) {
        return extractAllClaims(token).get("prenom", String.class);
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    /**
     * Extrait le claim "jti" (identifiant unique) d'un token, utilisé pour retrouver
     * la ligne RefreshToken correspondante en base. Retourne null si absent.
     */
    public String extractJti(String token) {
        return extractAllClaims(token).getId();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
