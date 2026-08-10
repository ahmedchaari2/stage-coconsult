import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Medecin, MedecinOption, UpdateMedecinRequest } from '../models/medecin.model';
import { PageDTO } from '../models/page.model';
import { environment } from 'src/environments/environment';

/** Filtres optionnels de GET /api/users/medecins (recherche avancée, voir UserFilter côté backend). */
export interface MedecinFilterParams {
  nom?: string;
  prenom?: string;
  specialite?: string;
  numeroOrdre?: string;
  actif?: boolean;
  q?: string;
  sansSpecialite?: boolean;
  sansNumeroOrdre?: boolean;
  sort?: string;
  direction?: 'asc' | 'desc';
}

@Injectable({
  providedIn: 'root'
})
export class MedecinService {
  private readonly API_URL = `${environment.apiUrl}/users`;
  private readonly http = inject(HttpClient);

  /**
   * Récupère une page de médecins (utilisateurs avec rôle MEDECIN), portée globale ADMIN
   * (GET /api/users/medecins?page=&size=&...filtres). `q` est une recherche combinée backend
   * (nom OU prenom OU email, LIKE insensible à la casse) — voir UserSpecifications côté
   * backend. Les autres champs de `filters` s'appliquent en ET, en plus de `q`.
   * Retourne actifs ET inactifs (avec leur statut).
   * Nécessite une authentification ADMIN (token ajouté automatiquement par l'intercepteur)
   */
  getMedecins(page = 0, size = 10, filters: MedecinFilterParams = {}): Observable<PageDTO<Medecin>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.q) params = params.set('q', filters.q);
    if (filters.nom) params = params.set('nom', filters.nom);
    if (filters.prenom) params = params.set('prenom', filters.prenom);
    if (filters.specialite) params = params.set('specialite', filters.specialite);
    if (filters.numeroOrdre) params = params.set('numeroOrdre', filters.numeroOrdre);
    if (filters.actif != null) params = params.set('actif', filters.actif);
    if (filters.sansSpecialite) params = params.set('sansSpecialite', true);
    if (filters.sansNumeroOrdre) params = params.set('sansNumeroOrdre', true);
    if (filters.sort) params = params.set('sort', filters.sort);
    if (filters.direction) params = params.set('direction', filters.direction);
    return this.http.get<PageDTO<Medecin>>(`${this.API_URL}/medecins`, { params });
  }

  /**
   * Récupère tous les médecins sous forme d'options légères (id, nom, prenom),
   * pour peupler les selects "Médecin référent" (GET /api/users/medecins/all).
   * Non paginé : contrairement à `getMedecins`, renvoie la liste complète.
   */
  getAllMedecinOptions(): Observable<MedecinOption[]> {
    return this.http.get<MedecinOption[]>(`${this.API_URL}/medecins/all`);
  }

  /**
   * Récupère le détail d'un médecin par son id (GET /api/users/{id}).
   * Vérifie côté backend que le médecin a bien été invité par l'ADMIN connecté.
   */
  getMedecin(id: number): Observable<Medecin> {
    return this.http.get<Medecin>(`${this.API_URL}/${id}`);
  }

  /**
   * Met à jour le nom/prénom/email d'un médecin (PUT /api/users/{id}).
   * Le rôle n'est pas modifiable. Vérifie invitedBy = ADMIN connecté côté backend.
   */
  updateMedecin(id: number, request: UpdateMedecinRequest): Observable<Medecin> {
    return this.http.put<Medecin>(`${this.API_URL}/${id}`, request);
  }

  /**
   * Bascule le statut actif/inactif d'un médecin (PUT /api/users/{id}/toggle-status).
   * Renvoie le médecin mis à jour (avec son nouveau statut) pour rafraîchir l'affichage.
   * Vérifie invitedBy = ADMIN connecté côté backend.
   */
  toggleMedecinStatus(id: number): Observable<Medecin> {
    return this.http.put<Medecin>(`${this.API_URL}/${id}/toggle-status`, {});
  }
}
