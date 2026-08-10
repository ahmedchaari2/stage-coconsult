package tn.coconsult.medtrack.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAppointmentRequest {
    @NotNull(message = "Le patient est obligatoire")
    private Long patientId;

    private Long medecinId;

    @NotNull(message = "La date et l'heure du rendez-vous sont obligatoires")
    private LocalDateTime dateHeure;

    @NotBlank(message = "Le motif est obligatoire")
    private String motif;

    private String notes;
}
