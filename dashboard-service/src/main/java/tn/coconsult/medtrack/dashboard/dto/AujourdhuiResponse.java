package tn.coconsult.medtrack.dashboard.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record AujourdhuiResponse(
        boolean disponible,
        List<RendezVousJourItem> rendezVous,
        long aConfirmer,
        long annulesAujourdhui,
        List<ProchainePlageLibre> prochainesPlagesLibres) {

    public static AujourdhuiResponse unavailable() {
        return new AujourdhuiResponse(false, List.of(), 0, 0, List.of());
    }

    public record RendezVousJourItem(
            Long id, LocalDateTime dateHeure, String statut,
            Long patientId, String patientNom, String patientPrenom,
            Long medecinId, String medecinNom, String medecinPrenom,
            String motif) {
    }

    public record ProchainePlageLibre(Long medecinId, String medecinNom, String medecinPrenom, LocalTime heure) {
    }
}
