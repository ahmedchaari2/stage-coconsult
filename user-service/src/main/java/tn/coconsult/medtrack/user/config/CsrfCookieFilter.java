package tn.coconsult.medtrack.user.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Force la résolution et la persistance du token CSRF sur chaque requête (même les GET), pour
 * que le cookie XSRF-TOKEN soit écrit dès le bootstrap du frontend, avant tout login.
 * <p>
 * Positionné après SessionManagementFilter (voir SecurityConfig), pas juste après CsrfFilter :
 * SessionManagementFilter déclenche CsrfAuthenticationStrategy à chaque nouvelle Authentication
 * posée dans le SecurityContext, ce qui arrive ici à chaque requête authentifiée (JwtAuthFilter
 * recrée l'Authentication à chaque appel, stateless, pas de session persistée).
 * CsrfAuthenticationStrategy efface le cookie et régénère un nouveau token en simple attribut
 * de requête sans jamais le réécrire en Set-Cookie ; ce filtre relit cet attribut après coup et
 * le sauvegarde explicitement, avant que le corps de la réponse ne soit écrit (une fois la
 * réponse committée, tout header ajouté est silencieusement ignoré — d'où la position dans la
 * chaîne, avant AuthorizationFilter/DispatcherServlet).
 */
@RequiredArgsConstructor
public class CsrfCookieFilter extends OncePerRequestFilter {

    private static final String CSRF_TOKEN_ATTRIBUTE = "_csrf";

    private final CsrfTokenRepository csrfTokenRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CSRF_TOKEN_ATTRIBUTE);
        if (csrfToken != null) {
            csrfTokenRepository.saveToken(csrfToken, request, response);
        }
        filterChain.doFilter(request, response);
    }
}
