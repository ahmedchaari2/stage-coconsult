package tn.coconsult.medtrack.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.RequiredArgsConstructor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.http.HttpStatus;

/**
 * Contrairement aux autres services métier, l'identité vient du cookie accessToken revalidé par
 * JwtAuthFilter, pas des headers de la Gateway : le port est publié pour le WebSocket. Pas de
 * CSRF : le cookie est en SameSite=Lax et les seules écritures ici marquent des notifications lues.
 */
@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
@EnableScheduling
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                // Pas de CORS REST ici : géré par la Gateway. Le WebSocket (direct sur :8086) garde
                // sa propre restriction d'origine dans WebSocketConfig.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                // Sans session/cookie local à valider, un accès non authentifié doit renvoyer 401
                // (pas le 403 par défaut de Spring Security) : c'est le signal qu'une requête a
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(unauthorizedEntryPoint()))
                .authorizeHttpRequests(authorize -> authorize
                        // -> dispatch interne vers /error) se fait rejeter en 403 vide par la
                        .requestMatchers("/error").permitAll()
                        // Ouvert ici seulement : l'authentification du handshake est faite par
                        // JwtHandshakeInterceptor, qui refuse la connexion sans cookie valide.
                        .requestMatchers("/ws-notifications/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> response.sendError(HttpStatus.UNAUTHORIZED.value());
    }
}
