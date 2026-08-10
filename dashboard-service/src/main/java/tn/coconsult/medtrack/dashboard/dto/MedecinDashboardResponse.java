package tn.coconsult.medtrack.dashboard.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record MedecinDashboardResponse(
        RendezVousJourSection rendezVousJour,
        PatientsSuiviSection patients,
        ConsultationsSection consultations,
        TraitementsEcheanceSection traitementsAEcheance,
        PatientsSansDossierSection patientsSansDossier) {

    public record RendezVousJourSection(boolean disponible, int total, Map<String, Integer> parStatut, ProchainRdv prochain) {
        public static RendezVousJourSection unavailable() {
            return new RendezVousJourSection(false, 0, Map.of(), null);
        }
    }

    public record ProchainRdv(Long id, LocalDateTime dateHeure, String patientNom, String patientPrenom, String motif, String statut) {
    }

    public record PatientsSuiviSection(boolean disponible, long suivis, long nouveauxCeMois) {
        public static PatientsSuiviSection unavailable() {
            return new PatientsSuiviSection(false, 0, 0);
        }
    }

    public record ConsultationsSection(boolean disponible, long ceMois, long moisPrecedent, long variation) {
        public static ConsultationsSection unavailable() {
            return new ConsultationsSection(false, 0, 0, 0);
        }
    }

    public record TraitementsEcheanceSection(boolean disponible, int total, List<TraitementEcheance> items) {
        public static TraitementsEcheanceSection unavailable() {
            return new TraitementsEcheanceSection(false, 0, List.of());
        }
    }

    public record TraitementEcheance(Long prescriptionId, String medicament, LocalDate consultationDate,
                                     Integer dureeJours, LocalDate dateFin, long joursRestants) {
    }

    public record PatientsSansDossierSection(boolean disponible, int total, List<PatientLite> items) {
        public static PatientsSansDossierSection unavailable() {
            return new PatientsSansDossierSection(false, 0, List.of());
        }
    }

    public record PatientLite(Long patientId, String nom, String prenom, String numeroDossier) {
    }
}
