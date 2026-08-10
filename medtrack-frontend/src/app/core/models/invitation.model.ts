// Type pour l'envoi d'une invitation (ADMIN uniquement)
export interface InvitationRequest {
  email: string;
}

export interface InvitationResponse {
  message: string;
}

/**
 * À vérifier côté backend : POST /api/invitations/{token}/activate doit accepter et persister
 * specialite/numeroOrdre/telephone (même piège que UpdateUserRequest, voir medecin.model.ts).
 */
export interface ActivationRequest {
  password: string;
  nom: string;
  prenom: string;
  specialite?: string | null;
  numeroOrdre?: string | null;
  telephone?: string | null;
}

export interface InvitationValidation {
  email: string;
}

/** Statut réel d'une invitation (recalculé côté backend à la lecture, voir InvitationService). */
export type InvitationStatus = 'PENDING' | 'EXPIRED' | 'ACCEPTED' | 'REVOKED';

/** Ligne de la liste des invitations (GET /api/invitations, ADMIN uniquement). */
export interface InvitationSummary {
  id: number;
  email: string;
  role: string;
  status: InvitationStatus;
  expirationDate: string;
  createdAt: string;
  invitedByName: string | null;
}
