package tn.coconsult.medtrack.prescription.dto;

import java.time.LocalDate;

public record PatientDetailsResponse(String nom, String prenom, LocalDate dateNaissance, String numeroDossier) {
}
