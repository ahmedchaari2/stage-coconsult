package tn.coconsult.medtrack.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.coconsult.medtrack.appointment.model.StatutRendezVous;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAppointmentRequest {
    private Long medecinId;

    @NotNull(message = "La date et l'heure du rendez-vous sont obligatoires")
    private LocalDateTime dateHeure;

    @NotBlank(message = "Le motif est obligatoire")
    private String motif;

    @NotNull(message = "Le statut est obligatoire")
    private StatutRendezVous statut;

    private String notes;
}
