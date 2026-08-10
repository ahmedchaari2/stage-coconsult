package tn.coconsult.medtrack.dashboard.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AdminDashboardResponse(
        ActiviteParMedecinSection activiteParMedecin,
        OccupationSection tauxOccupation,
        AnnulationsSection annulationsParSemaine,
        RendezVous7JoursSection rendezVous7Jours,
        PatientsStatsSection patients,
        QualiteDonneesSection qualiteDonnees) {

    public record ActiviteParMedecinSection(boolean disponible, List<ActiviteMedecin> items) {
        public static ActiviteParMedecinSection unavailable() {
            return new ActiviteParMedecinSection(false, List.of());
        }
    }

    public record ActiviteMedecin(Long medecinId, String nom, String prenom, long rendezVous, long consultations, long prescriptions) {
    }

    public record OccupationSection(boolean disponible, String periode, long creneauxTheoriques, long rendezVousNonAnnules, double taux) {
        public static OccupationSection unavailable() {
            return new OccupationSection(false, null, 0, 0, 0d);
        }
    }

    public record AnnulationsSection(boolean disponible, List<AnnulationSemaine> items) {
        public static AnnulationsSection unavailable() {
            return new AnnulationsSection(false, List.of());
        }
    }

    public record AnnulationSemaine(String semaine, LocalDate debut, long total) {
    }

    public record RendezVous7JoursSection(boolean disponible, List<RdvParJour> items) {
        public static RendezVous7JoursSection unavailable() {
            return new RendezVous7JoursSection(false, List.of());
        }
    }

    public record RdvParJour(LocalDate date, long total) {
    }

    public record PatientsStatsSection(boolean disponible, long actifs, long archives,
                                       List<NouveauxMois> nouveauxParMois, Map<String, Long> parSexe, Map<String, Long> parTrancheAge) {
        public static PatientsStatsSection unavailable() {
            return new PatientsStatsSection(false, 0, 0, List.of(), Map.of(), Map.of());
        }
    }

    public record NouveauxMois(String mois, long total) {
    }

    public record QualiteDonneesSection(boolean disponible, CompteurFiltrable patientsSansNumeroDossier, CompteurFiltrable patientsSansCin,
                                        CompteurFiltrable medecinsSansSpecialite, CompteurFiltrable medecinsSansNumeroOrdre,
                                        CompteurFiltrable dossiersMedicauxManquants) {
        public static QualiteDonneesSection unavailable() {
            CompteurFiltrable vide = new CompteurFiltrable(0, Map.of());
            return new QualiteDonneesSection(false, vide, vide, vide, vide, vide);
        }
    }

    public record CompteurFiltrable(long total, Map<String, String> filtre) {
    }
}
