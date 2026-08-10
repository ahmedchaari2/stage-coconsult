package tn.coconsult.medtrack.dashboard.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PatientDto(
        Long id,
        String nom,
        String prenom,
        LocalDate dateNaissance,
        String sexe,
        String cin,
        String numeroDossier,
        Long medecinReferentId,
        boolean archived,
        LocalDateTime createdAt) {
}
