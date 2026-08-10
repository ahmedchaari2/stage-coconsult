package tn.coconsult.medtrack.notification.dto;

import tn.coconsult.medtrack.appointment.model.StatutRendezVous;

import java.time.LocalDateTime;

public record AppointmentNotificationData(
        String patientNom,
        String patientPrenom,
        String medecinNom,
        String medecinPrenom,
        LocalDateTime dateHeure,
        StatutRendezVous statut
) {
}
