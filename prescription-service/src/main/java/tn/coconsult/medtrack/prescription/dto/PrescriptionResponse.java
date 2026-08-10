package tn.coconsult.medtrack.prescription.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionResponse {
    private Long id;
    private Long consultationId;
    private String medicament;
    private String posologie;
    private Integer dureeJours;
    private String instructions;
    private boolean renouvelable;
    private boolean archived;
    private LocalDateTime archivedAt;

    private LocalDate consultationDate;
    private Long patientId;
    private String patientNom;
    private String patientPrenom;
    private Long medecinId;
    private String medecinNom;
    private String medecinPrenom;

    private String createdByName;
    private String updatedByName;
}
