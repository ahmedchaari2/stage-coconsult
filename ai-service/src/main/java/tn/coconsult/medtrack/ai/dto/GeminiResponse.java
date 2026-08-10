package tn.coconsult.medtrack.ai.dto;

import java.util.List;

public record GeminiResponse(List<GeminiCandidate> candidates) {
}
