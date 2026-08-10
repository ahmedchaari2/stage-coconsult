package tn.coconsult.medtrack.patient.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.coconsult.medtrack.patient.model.SexePatient;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientRequest {
    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    private String prenom;

    @NotNull(message = "La date de naissance est obligatoire")
    @PastOrPresent(message = "La date de naissance ne peut pas être dans le futur")
    private LocalDate dateNaissance;

    @NotBlank(message = "Le téléphone est obligatoire")
    @Pattern(regexp = "^(?:\\+216[ .-]?)?(?:\\d[ .-]?){8}$", message = "Format de téléphone tunisien invalide")
    private String telephone;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    private String email;

    @NotBlank(message = "L'adresse est obligatoire")
    private String adresse;

    // Champs métier optionnels : le patient reste créable sans eux (rétrocompatible avec les
    private SexePatient sexe;

    private String contactUrgenceNom;

    @Pattern(regexp = "^$|^(?:\\+216[ .-]?)?(?:\\d[ .-]?){8}$", message = "Format du téléphone d'urgence invalide")
    private String contactUrgenceTelephone;

    private String cin;

    private String numeroAssurance;

    private Long medecinReferentId;
}
