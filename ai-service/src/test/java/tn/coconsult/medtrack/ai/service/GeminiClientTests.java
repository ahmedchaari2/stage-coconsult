package tn.coconsult.medtrack.ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.ai.dto.GeminiCandidate;
import tn.coconsult.medtrack.ai.dto.GeminiContent;
import tn.coconsult.medtrack.ai.dto.GeminiPart;
import tn.coconsult.medtrack.ai.dto.GeminiResponse;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiClientTests {

    // apiKey/model ne sont peuples que par @Value dans un contexte Spring : construit ainsi,
    // apiKey reste null, exactement le scenario "clé absente" qu'on veut tester.
    private final GeminiClient geminiClient = new GeminiClient(18);

    @Test
    void generateFailsCleanlyWhenNoApiKeyIsConfigured() {
        assertThatThrownBy(() -> geminiClient.generate("system", "question"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Assistant indisponible, réessayez plus tard");
    }

    @Test
    void extractTextJoinsAndStripsAllParts() {
        GeminiResponse response = candidateWithParts("  Bonjour ", "le monde  ");

        assertThat(geminiClient.extractText(response)).isEqualTo("Bonjour le monde");
    }

    @Test
    void extractTextRejectsAResponseWithNoCandidates() {
        assertThatThrownBy(() -> geminiClient.extractText(new GeminiResponse(List.of())))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void extractTextRejectsANullResponse() {
        assertThatThrownBy(() -> geminiClient.extractText(null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void extractTextRejectsACandidateWithNoParts() {
        GeminiResponse response = new GeminiResponse(List.of(new GeminiCandidate(new GeminiContent(List.of()))));

        assertThatThrownBy(() -> geminiClient.extractText(response)).isInstanceOf(ResponseStatusException.class);
    }

    private GeminiResponse candidateWithParts(String... texts) {
        List<GeminiPart> parts = Arrays.stream(texts).map(GeminiPart::new).toList();
        return new GeminiResponse(List.of(new GeminiCandidate(new GeminiContent(parts))));
    }
}
