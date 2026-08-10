package tn.coconsult.medtrack.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String message, String sessionId) {
}
