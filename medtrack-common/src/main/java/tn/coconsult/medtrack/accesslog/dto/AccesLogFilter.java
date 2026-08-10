package tn.coconsult.medtrack.accesslog.dto;

import tn.coconsult.medtrack.accesslog.model.TypeRessource;
import tn.coconsult.medtrack.accesslog.model.TypeAction;

import java.time.LocalDateTime;

public record AccesLogFilter(Long utilisateurId, Long patientId, TypeRessource typeRessource,
                             TypeAction action, LocalDateTime dateFrom, LocalDateTime dateTo, String q) {
}
