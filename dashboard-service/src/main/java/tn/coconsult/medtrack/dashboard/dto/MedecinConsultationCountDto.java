package tn.coconsult.medtrack.dashboard.dto;

/**
 * Nombre de consultations non archivées d'un médecin sur une période, renvoyé par l'endpoint
 * interne de medicalrecord-service (les consultations n'ont pas de recherche transversale
 * publique). count est un long : Jackson accepte l'entier JSON.
 */
public record MedecinConsultationCountDto(Long medecinId, long count) {
}
