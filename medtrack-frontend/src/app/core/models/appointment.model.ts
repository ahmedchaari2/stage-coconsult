/**
 * Statuts possibles d'un rendez-vous (aligné sur l'enum backend StatutRendezVous).
 * Aucune machine à états : toute transition est acceptée par le backend, y compris
 * ANNULE -> PLANIFIE (choix explicite du client via le formulaire d'édition).
 */
export type AppointmentStatut = 'PLANIFIE' | 'CONFIRME' | 'ANNULE' | 'HONORE';

/**
 * Modèle Rendez-vous - réponse complète de l'API (GET /api/appointments, /calendar, /{id}).
 * patientNom/patientPrenom/medecinNom/medecinPrenom sont enrichis côté backend
 * (jointure applicative) : pas d'appel supplémentaire nécessaire pour les afficher.
 */
export interface Appointment {
  id: number;
  patientId: number;
  patientNom: string;
  patientPrenom: string;
  medecinId: number;
  medecinNom: string;
  medecinPrenom: string;
  dateHeure: string; // ISO LocalDateTime, ex. "2026-07-15T10:30:00"
  motif: string;
  statut: AppointmentStatut;
  notes: string | null;
}

export interface CreateAppointmentRequest {
  patientId: number;
  medecinId: number | null;
  dateHeure: string;
  motif: string;
  notes: string | null;
}

export interface UpdateAppointmentRequest {
  medecinId: number | null;
  dateHeure: string;
  motif: string;
  statut: AppointmentStatut;
  notes: string | null;
}

export interface PatientOption {
  id: number;
  nom: string;
  prenom: string;
}
