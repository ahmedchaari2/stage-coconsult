package tn.coconsult.medtrack.dashboard.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tn.coconsult.medtrack.user.model.User;

/**
 * Repropage l'identité déjà vérifiée par ce service (X-User-Id/X-User-Role, posés par
 * JwtAuthFilter) vers les appels Feign sortants. C'est ce qui fait que le scope de sécurité
 * des services appelés s'applique tel quel : un MEDECIN qui interroge son dashboard ne voit
 * que ses propres données, sans logique de filtrage à dupliquer ici.
 */
@Component
public class FeignAuthHeaderInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            template.header("X-User-Id", String.valueOf(user.getId()));
            template.header("X-User-Role", user.getRole().name());
        }
    }
}
