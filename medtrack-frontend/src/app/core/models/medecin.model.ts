export interface Medecin {
  id: number;
  email: string;
  nom: string;
  prenom: string;
  role: 'MEDECIN';
  /**
   * Statut du compte : true = actif (peut se connecter), false = désactivé.
   * Renseigné par le backend (GET /api/users/medecins renvoie actifs + inactifs).
   */
  actif: boolean;
  specialite?: string | null;
  numeroOrdre?: string | null;
  telephone?: string | null;
  createdAt?: string | null;
}

/**
 * Payload de mise à jour d'un médecin (PUT /api/users/{id}). Le rôle n'est jamais modifiable
 * côté frontend.
 *
 * ATTENTION : specialite/numeroOrdre sont envoyés ici mais UpdateUserRequest côté backend ne
 * les expose pas encore en écriture (seuls User/UserResponse les portent en lecture) — Jackson
 * les ignore silencieusement, sans erreur. À corriger côté backend pour que ça persiste vraiment.
 */
export interface UpdateMedecinRequest {
  nom: string;
  prenom: string;
  email: string;
  specialite?: string | null;
  numeroOrdre?: string | null;
  telephone?: string | null;
}

/**
 * Option légère pour les selects "Médecin référent" (GET /api/users/medecins/all).
 * Contrairement à `Medecin`, non paginée et sans les champs email/actif.
 */
export interface MedecinOption {
  id: number;
  nom: string;
  prenom: string;
}
