package tn.coconsult.medtrack.medicalrecord.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.coconsult.medtrack.medicalrecord.model.GroupeSanguin;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedicalRecordRequest {
    @NotNull(message = "Le groupe sanguin est obligatoire")
    private GroupeSanguin groupeSanguin;

    @NotBlank(message = "Les allergies sont obligatoires (indiquer \"aucune\" si absentes)")
    private String allergies;

    @NotBlank(message = "Les antécédents sont obligatoires (indiquer \"aucun\" si absents)")
    private String antecedents;

    // Champs optionnels : le dossier reste créable/modifiable sans eux (rétrocompatible).
    private String traitementsChroniques;

    private String antecedentsFamiliaux;

    private String vaccinations;
}
