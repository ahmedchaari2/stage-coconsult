package tn.coconsult.medtrack.medicalrecord.dto;

import tn.coconsult.medtrack.accesslog.model.TypeRessource;
import tn.coconsult.medtrack.accesslog.model.TypeAction;

import java.time.LocalDateTime;

public record AccesLogResponse(
        Long id,
        Long utilisateurId,
        TypeRessource typeRessource,
        TypeAction action,
        Long ressourceId,
        Long patientId,
        String patientNom,
        String patientPrenom,
        String ressourceNom,
        String ressourcePrenom,
        LocalDateTime dateHeure
) {
}
