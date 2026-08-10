package tn.coconsult.medtrack.ai.dto;

import java.util.List;

public record GeminiRequest(
        List<GeminiContent> contents,
        GeminiContent systemInstruction,
        GeminiGenerationConfig generationConfig
) {
}
