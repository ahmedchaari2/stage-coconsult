/**
 * Groupe sanguin — valeurs exactes de l'enum backend
 * (tn.coconsult.medtrack.medicalrecord.model.GroupeSanguin), sérialisé en
 * @Enumerated(EnumType.STRING) : le nom de la constante Java est envoyé/reçu tel quel.
 */
export type GroupeSanguin = 'A_POSITIF' | 'A_NEGATIF' | 'B_POSITIF' | 'B_NEGATIF' | 'AB_POSITIF' | 'AB_NEGATIF' | 'O_POSITIF' | 'O_NEGATIF';

/**
 * Dossier médical d'un patient (GET/POST/PUT /api/patients/{patientId}/medical-record).
 * Un patient a au plus un dossier : absence -> 404.
 */
export interface MedicalRecord {
  id: number;
  patientId: number;
  groupeSanguin: GroupeSanguin;
  allergies: string;
  antecedents: string;
  traitementsChroniques?: string | null;
  antecedentsFamiliaux?: string | null;
  vaccinations?: string | null;
  createdBy?: string | null;
  createdAt?: string | null;
  modifiedBy?: string | null;
  updatedAt?: string | null;
}

export interface MedicalRecordRequest {
  groupeSanguin: GroupeSanguin;
  allergies: string;
  antecedents: string;
  traitementsChroniques?: string | null;
  antecedentsFamiliaux?: string | null;
  vaccinations?: string | null;
}

/**
 * Consultation rattachée à un dossier médical (GET paginé, POST/PUT/DELETE).
 * `medecinId` seul est renvoyé par le backend (comme `medecinReferentId` sur
 * Patient) : le nom/prénom est résolu côté frontend via la liste de
 * `MedecinOption` déjà chargée pour le médecin référent.
 */
export interface Consultation {
  id: number;
  medicalRecordId: number;
  medecinId: number;
  appointmentId?: number | null;
  consultationDate: string; // ISO date "YYYY-MM-DD"
  motif: string;
  diagnostic: string;
  notes: string | null;
  // Constantes vitales, toutes optionnelles (non mesurées -> null, jamais 0 par défaut).
  tensionArterielleSystolique?: number | null;
  tensionArterielleDiastolique?: number | null;
  poids?: number | null; // kg
  temperature?: number | null; // °C
  pouls?: number | null; // battements/min
  archived?: boolean;
  archivedAt?: string | null;
  createdBy?: string | null;
  createdAt?: string | null;
  modifiedBy?: string | null;
  updatedAt?: string | null;
}

export interface ConsultationRequest {
  consultationDate: string; // ISO date "YYYY-MM-DD"
  motif: string;
  diagnostic: string;
  notes?: string | null;
  tensionArterielleSystolique?: number | null;
  tensionArterielleDiastolique?: number | null;
  poids?: number | null;
  temperature?: number | null;
  pouls?: number | null;
  /**
   * Optionnel : obligatoire si l'appelant est ADMIN (400 sinon, validé en service),
   * ignoré et forcé à l'id du médecin connecté si l'appelant est MEDECIN.
   */
  medecinId?: number | null;
  /** Rendez-vous d'origine, optionnel et vérifié comme appartenant au même patient. */
  appointmentId?: number | null;
}

/** Réponse de GET /api/consultations/by-appointment/{appointmentId}. */
export interface ConsultationExists {
  exists: boolean;
  consultationId: number | null;
}

/**
 * Filtres optionnels pour GET /api/medical-records/{id}/consultations.
 * motif/medecinId/dateFrom/dateTo restent non exposés dans l'UI pour l'instant ;
 * le type existe pour que `getConsultations` reste extensible sans changer sa signature.
 */
export interface ConsultationFilters {
  motif?: string;
  medecinId?: number;
  dateFrom?: string; // ISO date "YYYY-MM-DD"
  dateTo?: string; // ISO date "YYYY-MM-DD"
  /**
   * Filtre exact (pas "inclusif") côté backend : ConsultationSpecifications.hasArchived
   * fait `archived == valeur`, donc `true` renvoie UNIQUEMENT les consultations archivées,
   * pas archivées + actives. Nom aligné sur le paramètre backend réel `archived`
   * (ConsultationController) — voir aussi PatientFilterParams.archived, même contrat.
   */
  archived?: boolean;
  /** Recherche multi-mots backend : motif OU diagnostic (voir ConsultationSpecifications.hasSearchTerm). */
  q?: string;
  sort?: string;
  direction?: 'asc' | 'desc';
}
