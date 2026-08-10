package tn.coconsult.medtrack.prescription.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tn.coconsult.medtrack.user.model.User;

/** Repropage l'identité (X-User-Id/X-User-Role) vers les appels Feign sortants, sinon le service appelé rejette en 401. */
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
