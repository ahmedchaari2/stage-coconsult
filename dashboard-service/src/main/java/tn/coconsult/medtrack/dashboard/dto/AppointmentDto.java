package tn.coconsult.medtrack.dashboard.dto;

import java.time.LocalDateTime;

public record AppointmentDto(
        Long id,
        Long patientId,
        String patientNom,
        String patientPrenom,
        Long medecinId,
        String medecinNom,
        String medecinPrenom,
        LocalDateTime dateHeure,
        String motif,
        String statut) {
}
