package tn.coconsult.medtrack.medicalrecord.dto;

/**
 * Nombre de consultations non archivées d'un médecin sur une période. Contrat de l'endpoint
 * interne /api/consultations/internal/count-by-medecin, consommé par dashboard-service : les
 * consultations n'ont pas de recherche transversale publique, cet agrégat évite d'exposer la table.
 */
public record MedecinConsultationCount(Long medecinId, long count) {
}
