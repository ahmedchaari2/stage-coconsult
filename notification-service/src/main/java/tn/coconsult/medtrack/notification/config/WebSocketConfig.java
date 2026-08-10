package tn.coconsult.medtrack.notification.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Broker STOMP simple en mémoire (pas de relais externe type RabbitMQ) : suffisant pour une
 * notification temps réel best-effort. Un seul canal, /queue/appointments en destination
 * utilisateur, jamais de broadcast global.
 * <p>
 * setAllowedOriginPatterns restreint le handshake à l'origine du frontend. Le WebSocket est
 * appelé en direct sur :8086 (il ne passe pas par la Gateway, qui gère le CORS des routes REST),
 * donc c'est ici que vit sa restriction d'origine. C'est aussi la vraie protection contre le
 * cross-site WebSocket hijacking (le cookie httpOnly part
 * quand même sur un handshake cross-site, mais l'en-tête Origin ne peut pas être falsifié par du
 * JS) — le CSRF double-soumission ne s'applique pas ici, SockJS ne le transmettrait pas de toute façon.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final JwtHandshakeHandler jwtHandshakeHandler;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-notifications")
                .setAllowedOriginPatterns("http://localhost:4200")
                .addInterceptors(jwtHandshakeInterceptor)
                .setHandshakeHandler(jwtHandshakeHandler)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
    }
}
