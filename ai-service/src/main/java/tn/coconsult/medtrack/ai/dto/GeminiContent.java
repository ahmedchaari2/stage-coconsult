package tn.coconsult.medtrack.ai.dto;

import java.util.List;

public record GeminiContent(List<GeminiPart> parts) {

    public static GeminiContent ofText(String text) {
        return new GeminiContent(List.of(new GeminiPart(text)));
    }
}
