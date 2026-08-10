package tn.coconsult.medtrack.notification.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Construit le Principal de la session STOMP à partir de "userId", posé dans les attributs de
 * session par {@link JwtHandshakeInterceptor#beforeHandshake} une fois le JWT validé. Le handshake
 * échoue déjà (voir JwtHandshakeInterceptor) si l'utilisateur n'a pas pu être résolu, donc
 * l'attribut est toujours présent ici.
 */
@Component
public class JwtHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                       Map<String, Object> attributes) {
        Long userId = (Long) attributes.get("userId");
        return new StompPrincipal(String.valueOf(userId));
    }
}
