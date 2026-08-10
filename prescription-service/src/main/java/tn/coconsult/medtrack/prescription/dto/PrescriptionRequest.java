package tn.coconsult.medtrack.prescription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionRequest {
    @NotBlank(message = "Le médicament est obligatoire")
    private String medicament;

    @NotBlank(message = "La posologie est obligatoire")
    private String posologie;

    @Positive(message = "La durée doit être un nombre de jours positif")
    private Integer dureeJours;

    private String instructions;

    private boolean renouvelable;
}
