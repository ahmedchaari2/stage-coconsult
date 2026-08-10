package tn.coconsult.medtrack.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import tn.coconsult.medtrack.ai.client.PatientReferentClient;
import tn.coconsult.medtrack.ai.dto.ChatResponse;
import tn.coconsult.medtrack.ai.dto.PatientContext;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private static final String SYSTEM_INSTRUCTION = """
            Tu es un assistant clinique qui aide un médecin ou un administrateur à consulter le \
            dossier d'un patient. Le contexte patient fourni ci-dessous est la seule source \
            d'information autorisée : reste strictement factuel, ne complète jamais un champ \
            absent ou vide par une supposition, et indique explicitement qu'une information n'est \
            pas disponible plutôt que de l'inventer. Un historique de conversation peut précéder \
            la question : sers-t'en uniquement pour comprendre le sujet d'une question de suivi \
            ("quoi d'autre ?", "et pour les allergies ?"), jamais comme source d'information à la \
            place du contexte patient. Réponds en français, en texte brut sans markdown, de façon \
            concise et directement exploitable par un professionnel de santé.""";

    private final PatientReferentClient patientReferentClient;
    private final PatientContextService patientContextService;
    private final GeminiClient geminiClient;
    private final ChatCache chatCache;
    private final ChatSessionHistory chatSessionHistory;
    private final ObjectMapper objectMapper;

    public ChatResponse chat(Long patientId, String message, String sessionId) {
        requireAccess(patientId);

        String cached = chatCache.get(patientId, message);
        if (cached != null) {
            chatSessionHistory.append(patientId, sessionId, message, cached);
            return new ChatResponse(cached);
        }

        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            PatientContext context = patientContextService.build(patientId);
            List<ChatSessionHistory.Turn> history = chatSessionHistory.recentTurns(patientId, sessionId);
            String prompt = buildPrompt(context, history, message);
            String reponse = geminiClient.generate(SYSTEM_INSTRUCTION, prompt);
            chatCache.put(patientId, message, reponse);
            chatSessionHistory.append(patientId, sessionId, message, reponse);
            success = true;
            return new ChatResponse(reponse);
        } finally {
            log.info("chat ia patientId={} dureeMs={} succes={}", patientId, System.currentTimeMillis() - start, success);
        }
    }

    private String buildPrompt(PatientContext context, List<ChatSessionHistory.Turn> history, String message) {
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Sérialisation du contexte patient impossible", exception);
        }

        StringBuilder prompt = new StringBuilder();
        if (!history.isEmpty()) {
            prompt.append("Historique de la conversation (du plus ancien au plus récent) :\n---\n");
            for (ChatSessionHistory.Turn turn : history) {
                prompt.append("Médecin : ").append(turn.question()).append('\n');
                prompt.append("Assistant : ").append(turn.reponse()).append('\n');
            }
            prompt.append("---\n\n");
        }

        prompt.append("Contexte patient (JSON) :\n---\n").append(contextJson).append("\n---\n\n");
        prompt.append("Question du médecin : ").append(message);
        return prompt.toString();
    }

    private void requireAccess(Long patientId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();

        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (currentUser.getRole() == Role.MEDECIN && patientReferentClient.isReferent(patientId, currentUser.getId())) {
            return;
        }
        throw new AccessDeniedException("Accès refusé : ce patient ne vous est pas assigné");
    }
}
