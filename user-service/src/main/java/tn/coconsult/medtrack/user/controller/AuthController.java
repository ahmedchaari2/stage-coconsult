package tn.coconsult.medtrack.user.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.coconsult.medtrack.common.dto.MessageResponse;
import tn.coconsult.medtrack.user.dto.ForgotPasswordRequest;
import tn.coconsult.medtrack.user.dto.LoginRequest;
import tn.coconsult.medtrack.user.dto.LoginResponse;
import tn.coconsult.medtrack.user.dto.LoginResult;
import tn.coconsult.medtrack.user.dto.ResetPasswordRequest;
import tn.coconsult.medtrack.user.dto.UserResponse;
import tn.coconsult.medtrack.user.mapper.UserMapper;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.service.AuthService;
import tn.coconsult.medtrack.user.service.PasswordResetService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final UserMapper userMapper;
    private final CsrfTokenRepository csrfTokenRepository;

    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${jwt.access-token-expiration-minutes}")
    private long accessTokenExpirationMinutes;

    @Value("${jwt.refresh-token-expiration-days}")
    private long refreshTokenExpirationDays;

    /**
     * Login : pose DEUX cookies httpOnly (accessToken court + refreshToken long) ainsi
     * qu'un cookie XSRF-TOKEN NON-httpOnly pour la protection CSRF. Le body ne contient
     * AUCUN token, uniquement les infos utilisateur.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse response) {
        try {
            LoginResult result = authService.login(request);
            addAccessTokenCookie(response, result.accessToken());
            addRefreshCookie(response, result.refreshToken());
            addCsrfCookie(httpRequest, response);
            return ResponseEntity.ok(result.loginResponse());
        } catch (BadCredentialsException exception) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }
    }

    /**
     * Renouvelle la paire de tokens à partir du refresh token présenté via cookie httpOnly.
     * Pose un NOUVEAU cookie accessToken (en plus du refreshToken roté). Renvoie un body
     * vide + 200 : aucun token dans le corps de réponse.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, "refreshToken");
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        LoginResult result = authService.refreshTokens(refreshToken);
        addAccessTokenCookie(response, result.accessToken());
        addRefreshCookie(response, result.refreshToken());
        addCsrfCookie(request, response);
        return ResponseEntity.ok().build();
    }

    /**
     * Déconnexion : révoque le refresh token en base, efface accessToken et refreshToken
     * (Max-Age=0), et repose un cookie XSRF-TOKEN frais (comme login/refresh) plutôt que
     * de le supprimer — sans ça, la toute première tentative de login suivante échoue
     * faute de cookie CSRF valide (un token CSRF non lié à une session authentifiée
     * n'est pas un risque de sécurité).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, "refreshToken");
        authService.logout(refreshToken);
        clearAccessTokenCookie(response);
        clearRefreshCookie(response);
        addCsrfCookie(request, response);
        return ResponseEntity.ok().build();
    }

    /**
     * Restauration de session côté frontend : renvoie l'utilisateur connecté si le cookie
     * accessToken est valide (peuplé dans le contexte de sécurité par JwtAuthFilter),
     * 401 sinon. GET → exempté de la vérification CSRF.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(new MessageResponse("Si ce compte existe, un email a été envoyé."));
    }

    /**
     * Réinitialisation effective du mot de passe à partir du token reçu par email (public).
     * 400 avec message explicite si le token est invalide, expiré ou déjà utilisé.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(new MessageResponse("Mot de passe réinitialisé avec succès."));
    }

    /**
     * Pose l'access token dans un cookie httpOnly + SameSite=Lax. Utilise ResponseCookie
     * (Spring) pour garantir le rendu correct de tous les attributs quel que soit le conteneur.
     */
    private void addAccessTokenCookie(HttpServletResponse response, String accessToken) {
        ResponseCookie cookie = ResponseCookie.from("accessToken", accessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(accessTokenExpirationMinutes * 60L)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Pose le refresh token dans un cookie httpOnly + SameSite=Lax (longue durée).
     */
    private void addRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(refreshTokenExpirationDays * 24L * 60L * 60L)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Pose le cookie XSRF-TOKEN (NON httpOnly, donc lisible par JS) contenant un token CSRF
     * aléatoire, pour la protection CSRF en double-soumission de cookie. Délègue la
     * génération au même CsrfTokenRepository que la chaîne de filtres, garantissant une
     * validation cohérente (le client renvoie ce token en header X-XSRF-TOKEN).
     */
    private void addCsrfCookie(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken csrfToken = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(csrfToken, request, response);
    }

    private void clearAccessTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
