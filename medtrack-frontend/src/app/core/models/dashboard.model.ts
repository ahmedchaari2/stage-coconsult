/**
 * Contrat des deux vues du tableau de bord (dashboard-service). Chaque section porte un drapeau
 * `disponible` : le backend agrège via Feign et, si un service source est en panne, renvoie la
 * section à false plutôt que d'échouer globalement — le front dégrade alors cette carte seule.
 */

// --- Vue MEDECIN ---

export interface ProchainRdv {
  id: number;
  dateHeure: string; // ISO LocalDateTime
  patientNom: string;
  patientPrenom: string;
  motif: string;
  statut: string;
}

export interface RendezVousJourSection {
  disponible: boolean;
  total: number;
  parStatut: Record<string, number>;
  prochain: ProchainRdv | null;
}

export interface PatientsSuiviSection {
  disponible: boolean;
  suivis: number;
  nouveauxCeMois: number;
}

export interface ConsultationsSection {
  disponible: boolean;
  ceMois: number;
  moisPrecedent: number;
  variation: number;
}

export interface TraitementEcheance {
  prescriptionId: number;
  medicament: string;
  consultationDate: string; // ISO date
  dureeJours: number;
  dateFin: string; // ISO date
  joursRestants: number;
}

export interface TraitementsEcheanceSection {
  disponible: boolean;
  total: number;
  items: TraitementEcheance[];
}

export interface PatientLite {
  patientId: number;
  nom: string;
  prenom: string;
  numeroDossier: string | null;
}

export interface PatientsSansDossierSection {
  disponible: boolean;
  total: number;
  items: PatientLite[];
}

export interface MedecinDashboard {
  rendezVousJour: RendezVousJourSection;
  patients: PatientsSuiviSection;
  consultations: ConsultationsSection;
  traitementsAEcheance: TraitementsEcheanceSection;
  patientsSansDossier: PatientsSansDossierSection;
}

// --- Vue ADMIN ---

export interface ActiviteMedecin {
  medecinId: number;
  nom: string;
  prenom: string;
  rendezVous: number;
  consultations: number;
  prescriptions: number;
}

export interface ActiviteParMedecinSection {
  disponible: boolean;
  items: ActiviteMedecin[];
}

export interface OccupationSection {
  disponible: boolean;
  periode: string | null; // "YYYY-MM-DD/YYYY-MM-DD", semaine glissante [aujourd'hui, +6 jours]
  creneauxTheoriques: number;
  rendezVousNonAnnules: number;
  taux: number; // ratio 0..1
}

export interface AnnulationSemaine {
  semaine: string; // "YYYY-Www"
  debut: string; // ISO date (lundi)
  total: number;
}

export interface AnnulationsSection {
  disponible: boolean;
  items: AnnulationSemaine[];
}

export interface RdvParJour {
  date: string; // ISO date
  total: number;
}

export interface RendezVous7JoursSection {
  disponible: boolean;
  items: RdvParJour[];
}

export interface NouveauxMois {
  mois: string; // "YYYY-MM"
  total: number;
}

export interface PatientsStatsSection {
  disponible: boolean;
  actifs: number;
  archives: number;
  nouveauxParMois: NouveauxMois[];
  parSexe: Record<string, number>;
  parTrancheAge: Record<string, number>;
}

/** Un compteur qui mène quelque part : filtre = query params de la liste préfiltrée correspondante (vide si aucune). */
export interface CompteurFiltrable {
  total: number;
  filtre: Record<string, string>;
}

export interface QualiteDonneesSection {
  disponible: boolean;
  patientsSansNumeroDossier: CompteurFiltrable;
  patientsSansCin: CompteurFiltrable;
  medecinsSansSpecialite: CompteurFiltrable;
  medecinsSansNumeroOrdre: CompteurFiltrable;
  dossiersMedicauxManquants: CompteurFiltrable;
}

export interface AdminDashboard {
  activiteParMedecin: ActiviteParMedecinSection;
  tauxOccupation: OccupationSection;
  annulationsParSemaine: AnnulationsSection;
  rendezVous7Jours: RendezVous7JoursSection;
  patients: PatientsStatsSection;
  qualiteDonnees: QualiteDonneesSection;
}

// --- Vue "aujourd'hui" (ADMIN + MEDECIN) ---

export interface RendezVousJourItem {
  id: number;
  dateHeure: string; // ISO LocalDateTime
  statut: string;
  patientId: number;
  patientNom: string;
  patientPrenom: string;
  medecinId: number;
  medecinNom: string;
  medecinPrenom: string;
  motif: string;
}

export interface ProchainePlageLibre {
  medecinId: number;
  medecinNom: string;
  medecinPrenom: string;
  heure: string; // "HH:mm"
}

export interface AujourdhuiResponse {
  disponible: boolean;
  rendezVous: RendezVousJourItem[];
  aConfirmer: number;
  annulesAujourdhui: number;
  prochainesPlagesLibres: ProchainePlageLibre[];
}
