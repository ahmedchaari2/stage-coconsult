package tn.coconsult.medtrack.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.RequiredArgsConstructor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.session.SessionManagementFilter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.http.HttpMethod;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
@EnableScheduling
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    // Cookie XSRF-TOKEN (non httpOnly) comparé au header X-XSRF-TOKEN (double-soumission).
    // Partagé avec AuthController qui régénère ce cookie sur login/refresh.
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Handler non masqué : le header X-XSRF-TOKEN doit porter la valeur brute du
                // cookie, pas une valeur XORée (le handler par défaut donnait un 403 même avec
                // cookie/header identiques). /ws-notifications exempté car SockJS ne connaît pas
                // notre header CSRF ; ce endpoint est protégé autrement (JWT au handshake + origin
                // check WebSocket).
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/ws-notifications/**"))
                // n'a pas lieu d'être ici (voir CsrfCookieFilter pour la gestion du cookie CSRF,
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .authorizeHttpRequests(authorize -> authorize
                        // -> dispatch interne vers /error) se fait rejeter en 403 vide par la
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout", "/api/auth/forgot-password",
                                "/api/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/invitations/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/invitations/*/activate").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/profile-photos/**").permitAll()
                        // cookie JWT : un appelant interne, qui ne transporte que les headers
                        // X-User-Id/X-User-Role, ne peut pas s'authentifier ici. Reste protégé
                        // de l'extérieur par la Gateway, qui exige le cookie sur /api/users/**
                        // (voir JwtAuthGatewayFilter) — comme tout le reste, ce port ne doit
                        .requestMatchers(HttpMethod.GET, "/api/users/*/summary").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/internal/medecins").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // Après SessionManagementFilter, pas juste après CsrfFilter : voir CsrfCookieFilter.
                .addFilterAfter(new CsrfCookieFilter(csrfTokenRepository()), SessionManagementFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
