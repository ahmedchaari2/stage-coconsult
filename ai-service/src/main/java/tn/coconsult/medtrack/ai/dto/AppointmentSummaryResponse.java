package tn.coconsult.medtrack.ai.dto;

import java.time.LocalDateTime;

public record AppointmentSummaryResponse(LocalDateTime dateHeure, String motif, String statut) {
}
