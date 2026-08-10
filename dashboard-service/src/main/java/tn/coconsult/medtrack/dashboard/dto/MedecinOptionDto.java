package tn.coconsult.medtrack.dashboard.dto;

public record MedecinOptionDto(Long id, String nom, String prenom, String specialite, String numeroOrdre, Boolean actif) {
}
