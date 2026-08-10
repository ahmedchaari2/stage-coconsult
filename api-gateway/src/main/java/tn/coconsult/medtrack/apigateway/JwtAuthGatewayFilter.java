package tn.coconsult.medtrack.apigateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpMethod;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Valide le JWT du cookie "accessToken" avant de laisser passer une requete vers un service interne.
 * Duplique pour l'instant la logique de JwtService (user-service) - a factoriser dans medtrack-common
 * une fois qu'il aura une dependance web. Le service interne ne voit jamais le JWT brut, juste l'identite verifiee.
 */
@Component
public class JwtAuthGatewayFilter implements GlobalFilter, Ordered {

    // Routes publiques de user-service (pas de JWT a ce stade) : login et forgot/reset-password
    // ou c'est le token recu par email qui fait foi, verifie par user-service lui-meme.
    private static final Set<String> PUBLIC_POST_PATHS = Set.of(
            "/api/auth/login", "/api/auth/refresh",
            "/api/auth/forgot-password", "/api/auth/reset-password");

    // GET /api/auth/me sert de bootstrap au cookie XSRF-TOKEN avant tout login ; sans cette exemption
    // login se ferait rejeter par la protection CSRF faute de token.
    private static final String CSRF_BOOTSTRAP_PATH = "/api/auth/me";

    // cookie ; c'est le token a usage unique de l'URL que user-service verifie. La creation reste protegee (ADMIN).
    private static final Pattern INVITATION_CHECK_PATH = Pattern.compile("^/api/invitations/[^/]+$");
    private static final Pattern INVITATION_ACTIVATE_PATH = Pattern.compile("^/api/invitations/[^/]+/activate$");

    // Photos de profil chargees via <img>, pas via le HttpClient Angular : pas de cookie envoye en
    private static final Pattern PROFILE_PHOTO_PATH = Pattern.compile("^/uploads/profile-photos/[^/]+$");
    private static final Pattern INTERNAL_PATH = Pattern.compile("^/api/.*/internal(?:/.*)?$");
    private static final Pattern CONSULTATION_SUMMARY_PATH = Pattern.compile("^/api/consultations/[^/]+/summary$");
    private static final Pattern USER_SUMMARY_PATH = Pattern.compile("^/api/users/[^/]+/summary$");

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (INTERNAL_PATH.matcher(path).matches()
                || CONSULTATION_SUMMARY_PATH.matcher(path).matches()
                || USER_SUMMARY_PATH.matcher(path).matches()) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }

        boolean publicPost = request.getMethod() == HttpMethod.POST && PUBLIC_POST_PATHS.contains(path);
        boolean csrfBootstrapGet = request.getMethod() == HttpMethod.GET && CSRF_BOOTSTRAP_PATH.equals(path);
        boolean invitationCheck = request.getMethod() == HttpMethod.GET
                && INVITATION_CHECK_PATH.matcher(path).matches();
        boolean invitationActivate = request.getMethod() == HttpMethod.POST
                && INVITATION_ACTIVATE_PATH.matcher(path).matches();
        boolean profilePhoto = request.getMethod() == HttpMethod.GET
                && PROFILE_PHOTO_PATH.matcher(path).matches();
        if (publicPost || csrfBootstrapGet || invitationCheck || invitationActivate || profilePhoto) {
            return chain.filter(exchange);
        }

        HttpCookie cookie = request.getCookies().getFirst("accessToken");

        if (cookie == null) {
            return unauthorized(exchange);
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(cookie.getValue())
                    .getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            return unauthorized(exchange);
        }

        Object userId = claims.get("id");
        String role = claims.get("role", String.class);

        // On ne retire pas le cookie pour user-service (il gere son propre CSRF/logout) ni pour
        // notification-service (son port etant publie pour le WebSocket, il revalide le JWT
        // lui-meme au lieu de se fier aux headers). Les autres ne doivent jamais voir le JWT brut.
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        boolean stripCookie = route != null
                && !"user-service".equals(route.getId())
                && !"notification-service".equals(route.getId());

        ServerHttpRequest.Builder mutation = request.mutate().headers(headers -> {
            headers.remove("X-User-Id");
            headers.remove("X-User-Role");
            headers.set("X-User-Id", userId == null ? "" : String.valueOf(userId));
            headers.set("X-User-Role", role == null ? "" : role);
        });
        if (stripCookie) {
            mutation.headers(headers -> headers.remove(org.springframework.http.HttpHeaders.COOKIE));
        }

        return chain.filter(exchange.mutate().request(mutation.build()).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
