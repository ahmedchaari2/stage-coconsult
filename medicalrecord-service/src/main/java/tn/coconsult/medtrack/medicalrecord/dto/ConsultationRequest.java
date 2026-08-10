package tn.coconsult.medtrack.medicalrecord.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationRequest {
    @NotNull(message = "La date de consultation est obligatoire")
    @PastOrPresent(message = "La date de consultation ne peut pas être dans le futur")
    private LocalDate consultationDate;

    @NotBlank(message = "Le motif est obligatoire")
    private String motif;

    @NotBlank(message = "Le diagnostic est obligatoire")
    private String diagnostic;

    private String notes;

    @Min(value = 50, message = "Tension systolique hors plage plausible (50-300 mmHg)")
    @Max(value = 300, message = "Tension systolique hors plage plausible (50-300 mmHg)")
    private Integer tensionArterielleSystolique;

    @Min(value = 30, message = "Tension diastolique hors plage plausible (30-200 mmHg)")
    @Max(value = 200, message = "Tension diastolique hors plage plausible (30-200 mmHg)")
    private Integer tensionArterielleDiastolique;

    @DecimalMin(value = "0.5", message = "Poids hors plage plausible (0.5-500 kg)")
    @DecimalMax(value = "500.0", message = "Poids hors plage plausible (0.5-500 kg)")
    private Double poids;

    @DecimalMin(value = "30.0", message = "Température hors plage plausible (30-45 °C)")
    @DecimalMax(value = "45.0", message = "Température hors plage plausible (30-45 °C)")
    private Double temperature;

    @Min(value = 20, message = "Pouls hors plage plausible (20-300 bpm)")
    @Max(value = 300, message = "Pouls hors plage plausible (20-300 bpm)")
    private Integer pouls;

    private Long medecinId;

    private Long appointmentId;
}
