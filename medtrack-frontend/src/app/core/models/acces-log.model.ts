/** Types de ressource tracés par le journal d'accès (aligné sur l'enum backend TypeRessource). */
export type TypeRessource = 'PATIENT' | 'MEDECIN' | 'RENDEZ_VOUS' | 'DOSSIER_MEDICAL' | 'CONSULTATION' | 'PRESCRIPTION';
export type TypeAction = 'VIEW' | 'CREATE' | 'UPDATE' | 'ARCHIVE' | 'RESTORE' | 'DELETE' | 'PRINT' | 'RENEW' | 'STATUS_CHANGE';

/** Ligne du journal d'accès (GET /api/acces-logs, réservé ADMIN). */
export interface AccesLog {
  id: number;
  utilisateurId: number;
  typeRessource: TypeRessource;
  action: TypeAction;
  ressourceId: number;
  patientId: number | null;
  patientNom?: string | null;
  patientPrenom?: string | null;
  ressourceNom?: string | null;
  ressourcePrenom?: string | null;
  dateHeure: string; // ISO LocalDateTime
}
