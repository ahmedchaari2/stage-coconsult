package tn.coconsult.medtrack.notification.config;

import java.security.Principal;

/**
 * Principal minimal associé à une session STOMP, porteur uniquement de l'id (String) du User
 * authentifié au handshake (voir JwtHandshakeInterceptor/JwtHandshakeHandler). Utilisé comme clé
 * par SimpMessagingTemplate.convertAndSendToUser : AppointmentNotificationService envoie vers
 * String.valueOf(userId), qui doit correspondre exactement à getName() ici.
 */
public record StompPrincipal(String name) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
