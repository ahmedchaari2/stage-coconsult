package tn.coconsult.medtrack.user.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTests {

    @Mock JwtService jwtService;
    @Mock UserRepository userRepository;
    @Mock FilterChain filterChain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtService, userRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void letsRequestsWithoutAnAccessTokenCookieThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService, userRepository);
    }

    @Test
    void rejectsAnExpiredOrInvalidToken() throws Exception {
        MockHttpServletRequest request = withAccessToken("token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.isTokenValid("token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Token invalide ou expiré");
    }

    // Régression : le filtre résolvait auparavant l'utilisateur par l'email du token (le sub du
    // JWT, figé à l'émission). Un changement d'email via le profil laissait l'ancien cookie
    // pointer vers un email que plus personne n'a, rejetant le titulaire du token de son propre
    // compte. L'id est stable, l'email ne l'est pas.
    @Test
    void resolvesTheUserByIdEvenIfTheEmailInTheTokenIsStale() throws Exception {
        MockHttpServletRequest request = withAccessToken("token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        User user = activeUser(7L, "nouvel.email@medtrack.tn");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.extractUserId("token")).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(user);
        verify(userRepository, never()).findByEmail(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rejectsATokenWhoseIdNoLongerMatchesAnyAccount() throws Exception {
        MockHttpServletRequest request = withAccessToken("token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.extractUserId("token")).thenReturn(99L);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Utilisateur introuvable");
    }

    @Test
    void blocksADeactivatedAccountEvenWithAValidToken() throws Exception {
        MockHttpServletRequest request = withAccessToken("token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        User user = activeUser(7L, "medecin@medtrack.tn");
        user.setActif(false);
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.extractUserId("token")).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest withAccessToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(JwtCookieUtils.ACCESS_TOKEN_COOKIE, token));
        return request;
    }

    private User activeUser(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setNom("Test");
        user.setPrenom("Test");
        user.setRole(Role.MEDECIN);
        user.setActif(true);
        return user;
    }
}
