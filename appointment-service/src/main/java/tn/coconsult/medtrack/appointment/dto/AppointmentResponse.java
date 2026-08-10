package tn.coconsult.medtrack.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.coconsult.medtrack.appointment.model.StatutRendezVous;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponse {
    private Long id;
    private Long patientId;
    private String patientNom;
    private String patientPrenom;

    private Long medecinId;
    private String medecinNom;
    private String medecinPrenom;

    private LocalDateTime dateHeure;
    private String motif;
    private StatutRendezVous statut;
    private String notes;
}
