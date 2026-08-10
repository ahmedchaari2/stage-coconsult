package tn.coconsult.medtrack.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.ai.client.PatientReferentClient;
import tn.coconsult.medtrack.ai.dto.ChatResponse;
import tn.coconsult.medtrack.ai.dto.PatientContext;
import tn.coconsult.medtrack.ai.service.ChatSessionHistory.Turn;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTests {

    private static final String SESSION_ID = "session-1";

    @Mock PatientReferentClient patientReferentClient;
    @Mock PatientContextService patientContextService;
    @Mock GeminiClient geminiClient;
    @Mock ChatCache chatCache;
    @Mock ChatSessionHistory chatSessionHistory;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks AiChatService aiChatService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminReachesGeminiWithoutAReferentCheck() {
        authenticate(1L, Role.ADMIN);
        when(chatCache.get(42L, "question")).thenReturn(null);
        when(chatSessionHistory.recentTurns(42L, SESSION_ID)).thenReturn(List.of());
        when(patientContextService.build(42L)).thenReturn(emptyContext());
        when(geminiClient.generate(anyString(), anyString())).thenReturn("reponse ia");

        ChatResponse response = aiChatService.chat(42L, "question", SESSION_ID);

        assertThat(response.reponse()).isEqualTo("reponse ia");
        verify(patientReferentClient, never()).isReferent(anyLong(), anyLong());
    }

    @Test
    void referentMedecinIsAllowedThrough() {
        authenticate(9L, Role.MEDECIN);
        when(patientReferentClient.isReferent(42L, 9L)).thenReturn(true);
        when(chatCache.get(42L, "question")).thenReturn(null);
        when(chatSessionHistory.recentTurns(42L, SESSION_ID)).thenReturn(List.of());
        when(patientContextService.build(42L)).thenReturn(emptyContext());
        when(geminiClient.generate(anyString(), anyString())).thenReturn("reponse ia");

        ChatResponse response = aiChatService.chat(42L, "question", SESSION_ID);

        assertThat(response.reponse()).isEqualTo("reponse ia");
    }

    @Test
    void nonReferentMedecinIsRejectedBeforeTouchingCacheContextOrGemini() {
        authenticate(9L, Role.MEDECIN);
        when(patientReferentClient.isReferent(42L, 9L)).thenReturn(false);

        assertThatThrownBy(() -> aiChatService.chat(42L, "question", SESSION_ID)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(chatCache, patientContextService, geminiClient, chatSessionHistory);
    }

    @Test
    void aCacheHitNeverReachesContextAggregationOrGemini() {
        authenticate(1L, Role.ADMIN);
        when(chatCache.get(42L, "question")).thenReturn("reponse en cache");

        ChatResponse response = aiChatService.chat(42L, "question", SESSION_ID);

        assertThat(response.reponse()).isEqualTo("reponse en cache");
        verifyNoInteractions(patientContextService, geminiClient);
    }

    // Un doublon reste un vrai tour du point de vue du medecin : la question de suivi d'apres
    // doit pouvoir s'appuyer dessus, meme si la reponse vient du cache et pas d'un vrai appel.
    @Test
    void aCacheHitIsStillAppendedToSessionHistory() {
        authenticate(1L, Role.ADMIN);
        when(chatCache.get(42L, "question")).thenReturn("reponse en cache");

        aiChatService.chat(42L, "question", SESSION_ID);

        verify(chatSessionHistory).append(42L, SESSION_ID, "question", "reponse en cache");
    }

    @Test
    void authorizationRunsBeforeTheCacheLookup() {
        authenticate(9L, Role.MEDECIN);
        when(patientReferentClient.isReferent(42L, 9L)).thenReturn(false);

        assertThatThrownBy(() -> aiChatService.chat(42L, "question", SESSION_ID)).isInstanceOf(AccessDeniedException.class);

        // Un cache deja rempli par un autre appel autorise ne doit jamais fuiter vers un
        // utilisateur non autorise : verifie qu'on ne l'a meme pas consulte.
        verify(chatCache, never()).get(anyLong(), anyString());
    }

    @Test
    void aSuccessfulResponseIsCachedAndAppendedToSessionHistory() {
        authenticate(1L, Role.ADMIN);
        when(chatCache.get(42L, "question")).thenReturn(null);
        when(chatSessionHistory.recentTurns(42L, SESSION_ID)).thenReturn(List.of());
        when(patientContextService.build(42L)).thenReturn(emptyContext());
        when(geminiClient.generate(anyString(), anyString())).thenReturn("reponse ia");

        aiChatService.chat(42L, "question", SESSION_ID);

        verify(chatCache).put(42L, "question", "reponse ia");
        verify(chatSessionHistory).append(42L, SESSION_ID, "question", "reponse ia");
    }

    @Test
    void aGeminiFailureIsNeverCachedNorAddedToHistory() {
        authenticate(1L, Role.ADMIN);
        when(chatCache.get(42L, "question")).thenReturn(null);
        when(chatSessionHistory.recentTurns(42L, SESSION_ID)).thenReturn(List.of());
        when(patientContextService.build(42L)).thenReturn(emptyContext());
        when(geminiClient.generate(anyString(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Assistant indisponible, réessayez plus tard"));

        assertThatThrownBy(() -> aiChatService.chat(42L, "question", SESSION_ID)).isInstanceOf(ResponseStatusException.class);

        verify(chatCache, never()).put(eq(42L), anyString(), anyString());
        verify(chatSessionHistory, never()).append(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void includesPriorTurnsInThePromptSentToGemini() {
        authenticate(1L, Role.ADMIN);
        when(chatCache.get(42L, "et pour les allergies ?")).thenReturn(null);
        when(chatSessionHistory.recentTurns(42L, SESSION_ID))
                .thenReturn(List.of(new Turn("quelles sont ses allergies ?", "Aspirine.")));
        when(patientContextService.build(42L)).thenReturn(emptyContext());
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(geminiClient.generate(anyString(), promptCaptor.capture())).thenReturn("reponse ia");

        aiChatService.chat(42L, "et pour les allergies ?", SESSION_ID);

        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("Historique de la conversation");
        assertThat(prompt).contains("quelles sont ses allergies ?");
        assertThat(prompt).contains("Aspirine.");
    }

    @Test
    void omitsTheHistorySectionWhenThereIsNone() {
        authenticate(1L, Role.ADMIN);
        when(chatCache.get(42L, "question")).thenReturn(null);
        when(chatSessionHistory.recentTurns(42L, SESSION_ID)).thenReturn(List.of());
        when(patientContextService.build(42L)).thenReturn(emptyContext());
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(geminiClient.generate(anyString(), promptCaptor.capture())).thenReturn("reponse ia");

        aiChatService.chat(42L, "question", SESSION_ID);

        assertThat(promptCaptor.getValue()).doesNotContain("Historique de la conversation");
    }

    private PatientContext emptyContext() {
        return new PatientContext("PATIENT-42", 40, "HOMME", null, null, null, null, null, null, List.of(), List.of(), List.of());
    }

    private void authenticate(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
