package tn.coconsult.medtrack.patient.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.coconsult.medtrack.patient.model.SexePatient;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientResponse {
    private Long id;
    private String nom;
    private String prenom;
    private LocalDate dateNaissance;
    private String telephone;
    private String email;
    private String adresse;
    private SexePatient sexe;
    private String contactUrgenceNom;
    private String contactUrgenceTelephone;
    private String cin;
    private String numeroDossier;
    private String numeroAssurance;
    private Long medecinReferentId;
    private boolean archived;
    private LocalDateTime archivedAt;
    private LocalDateTime createdAt;
}
