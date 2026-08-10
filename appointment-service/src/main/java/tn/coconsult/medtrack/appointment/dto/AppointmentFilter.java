package tn.coconsult.medtrack.appointment.dto;

import tn.coconsult.medtrack.appointment.model.StatutRendezVous;

import java.time.LocalDateTime;

public record AppointmentFilter(
        Long patientId,
        Long medecinId,
        StatutRendezVous statut,
        LocalDateTime dateFrom,
        LocalDateTime dateTo,
        String q
) {
}
