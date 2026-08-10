package tn.coconsult.medtrack.ai.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tn.coconsult.medtrack.user.model.User;

/** Propagates the authenticated identity from the current request to outgoing Feign calls. */
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
