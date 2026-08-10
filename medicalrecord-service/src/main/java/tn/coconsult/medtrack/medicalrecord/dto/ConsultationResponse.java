package tn.coconsult.medtrack.medicalrecord.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationResponse {
    private Long id;
    private Long medicalRecordId;
    private Long medecinId;
    private Long appointmentId;
    private LocalDate consultationDate;
    private String motif;
    private String diagnostic;
    private String notes;
    private Integer tensionArterielleSystolique;
    private Integer tensionArterielleDiastolique;
    private Double poids;
    private Double temperature;
    private Integer pouls;
    private boolean archived;
    private LocalDateTime archivedAt;
    // avant l'auditing, ou action hors contexte authentifié).
    private String createdByName;
    private String updatedByName;
}
