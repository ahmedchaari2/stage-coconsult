import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User } from '../models/user.model';
import {
  ActivationRequest,
  InvitationRequest,
  InvitationResponse,
  InvitationSummary,
  InvitationValidation
} from '../models/invitation.model';
import { environment } from 'src/environments/environment';

@Injectable({
  providedIn: 'root'
})
export class InvitationService {
  private readonly API_URL = `${environment.apiUrl}/invitations`;
  private readonly http = inject(HttpClient);

  /**
   * Envoie une invitation à un médecin (ADMIN uniquement). Si une invitation non acceptée existe
   * déjà pour cet email (PENDING ou expirée), le backend la renouvelle au lieu de la refuser :
   * pas de 409 "déjà invité" à gérer côté appelant, l'appel réussit toujours dans ce cas.
   */
  sendInvitation(request: InvitationRequest): Observable<InvitationResponse> {
    return this.http.post<InvitationResponse>(this.API_URL, request);
  }

  /** Liste des invitations avec leur statut réel (ADMIN uniquement). */
  getInvitations(): Observable<InvitationSummary[]> {
    return this.http.get<InvitationSummary[]>(this.API_URL);
  }

  /** Révoque une invitation (ADMIN uniquement) : le lien d'activation devient inutilisable. */
  revokeInvitation(id: number): Observable<void> {
    return this.http.put<void>(`${this.API_URL}/${id}/revoke`, {});
  }

  /**
   * Valide un token d'invitation (public).
   * Retourne l'email associé si le token est valide.
   * Le backend renvoie 404 (inconnu), 410 (expiré) ou 409 (déjà utilisé) sinon.
   */
  getInvitationByToken(token: string): Observable<InvitationValidation> {
    return this.http.get<InvitationValidation>(`${this.API_URL}/${token}`);
  }

  activateInvitation(token: string, request: ActivationRequest): Observable<User> {
    return this.http.post<User>(`${this.API_URL}/${token}/activate`, request);
  }
}
