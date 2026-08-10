export interface Prescription {
  id: number;
  consultationId: number;
  medicament: string;
  posologie: string;
  dureeJours: number | null;
  instructions: string | null;
  renouvelable: boolean;
  archived: boolean;
  archivedAt?: string | null;
  // Jointure applicative backend (PrescriptionService.enrichBatch), absente si la
  consultationDate?: string | null;
  patientId: number;
  patientNom?: string | null;
  patientPrenom?: string | null;
  medecinId?: number | null;
  medecinNom?: string | null;
  medecinPrenom?: string | null;
  createdByName?: string | null;
  updatedByName?: string | null;
}

export interface PrescriptionRequest {
  medicament: string;
  posologie: string;
  dureeJours: number | null;
  instructions?: string | null;
  /** Traitement au long cours vs ponctuel. Absent -> false par défaut côté backend. */
  renouvelable?: boolean;
}

/**
 * Filtres optionnels de GET /api/prescriptions. patientId/medecinId/dateFrom/dateTo portent
 * tous sur la consultation liée (résolus côté backend via PrescriptionService.
 * resolveConsultationIdsForFilter) ; `archived` est un filtre exact (pas "inclusif"), même
 * contrat que ConsultationFilters.archived — voir PrescriptionSpecifications.hasArchived.
 */
export interface PrescriptionFilters {
  patientId?: number;
  medecinId?: number;
  dateFrom?: string; // "YYYY-MM-DD"
  dateTo?: string; // "YYYY-MM-DD"
  archived?: boolean;
  q?: string;
  /** Calculé (consultation + durée vs aujourd'hui), pas une colonne — voir PrescriptionService côté backend. */
  statutCalcule?: 'ACTIVE' | 'EXPIREE';
  sort?: string;
  direction?: 'asc' | 'desc';
}
