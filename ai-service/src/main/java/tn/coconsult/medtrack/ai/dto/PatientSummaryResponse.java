package tn.coconsult.medtrack.ai.dto;

import java.time.LocalDate;

public record PatientSummaryResponse(LocalDate dateNaissance, String sexe) {
}
