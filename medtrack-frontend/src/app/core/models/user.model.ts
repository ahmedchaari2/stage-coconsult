export interface LoginRequest {
  email: string;
  password: string;
}

export interface User {
  id: number;
  email: string;
  nom: string;
  prenom: string;
  role: string;
  actif?: boolean;
  /** URL absolue de la photo de profil, ou null/undefined si aucune n'est définie. */
  photoUrl?: string | null;
  specialite?: string | null;
  numeroOrdre?: string | null;
  telephone?: string | null;
  createdAt?: string | null;
}

// Réponse de /login et /refresh : l'identité user uniquement. Aucun token
// n'est renvoyé dans le body (access + refresh sont des cookies httpOnly
// posés par le backend), donc pas de champ `token`.
export interface AuthResponse {
  id: number;
  email: string;
  nom: string;
  prenom: string;
  role: string;
  actif?: boolean;
  photoUrl?: string | null;
  specialite?: string | null;
  numeroOrdre?: string | null;
  telephone?: string | null;
  createdAt?: string | null;
}

/** Payload de PUT /api/users/me : role et actif ne sont volontairement pas modifiables ici. */
export interface UpdateProfileRequest {
  nom: string;
  prenom: string;
  email: string;
  telephone?: string | null;
  specialite?: string | null;
  numeroOrdre?: string | null;
}

/**
 * Payloads/réponses mot de passe oublié / réinitialisation / changement et photo de profil.
 * Contrat assumé, ces endpoints ne sont pas encore implémentés côté backend : à ajuster si
 * la forme réelle diffère une fois disponibles.
 */
export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/** `sessionsRevoked` : true si le backend a révoqué les autres sessions actives (autres refresh tokens). */
export interface ChangePasswordResponse {
  sessionsRevoked: boolean;
}
