package tn.coconsult.medtrack.medicalrecord.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.coconsult.medtrack.medicalrecord.model.GroupeSanguin;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedicalRecordResponse {
    private Long id;
    private Long patientId;
    private GroupeSanguin groupeSanguin;
    private String allergies;
    private String antecedents;
    private String traitementsChroniques;
    private String antecedentsFamiliaux;
    private String vaccinations;
    // l'auditing, ou action hors contexte authentifié).
    private String createdByName;
    private String updatedByName;
}
