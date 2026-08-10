import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Patient, PatientRequest } from '../models/patient.model';
import { PageDTO } from '../models/page.model';
import { environment } from 'src/environments/environment';

/** Filtres optionnels de GET /api/patients (recherche avancée, voir PatientFilter côté backend). */
export interface PatientFilterParams {
  nom?: string;
  prenom?: string;
  medecinReferentId?: number;
  telephone?: string;
  numeroDossier?: string;
  dateNaissanceFrom?: string; // "YYYY-MM-DD"
  dateNaissanceTo?: string; // "YYYY-MM-DD"
  q?: string;
  /**
   * Inclut les patients archivés dans le résultat (exclus par défaut côté backend).
   * Nom aligné sur le paramètre backend réel `archived` (PatientController) — PAS
   * `includeArchived`, qui n'existe pas côté serveur et était silencieusement ignoré.
   */
  archived?: boolean;
  sansNumeroDossier?: boolean;
  sansCin?: boolean;
  sort?: string;
  direction?: 'asc' | 'desc';
}

@Injectable({
  providedIn: 'root'
})
export class PatientService {
  private readonly API_URL = `${environment.apiUrl}/patients`;
  private readonly http = inject(HttpClient);

  /**
   * Récupère une page de patients (GET /api/patients?page=&size=&...filtres).
   * `q` est une recherche combinée backend, mot par mot (nom, prénom, email, téléphone ou
   * numéro de dossier — chaque mot doit matcher au moins un de ces champs, sur une ligne
   * différente si besoin, pour qu'une recherche "Nom Prénom" trouve le bon patient) ; les
   * autres champs de `filters` s'appliquent en ET, en plus de `q`.
   */
  getPatients(page = 0, size = 10, filters: PatientFilterParams = {}): Observable<PageDTO<Patient>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.q) params = params.set('q', filters.q);
    if (filters.nom) params = params.set('nom', filters.nom);
    if (filters.prenom) params = params.set('prenom', filters.prenom);
    if (filters.medecinReferentId != null) params = params.set('medecinReferentId', filters.medecinReferentId);
    if (filters.telephone) params = params.set('telephone', filters.telephone);
    if (filters.numeroDossier) params = params.set('numeroDossier', filters.numeroDossier);
    if (filters.dateNaissanceFrom) params = params.set('dateNaissanceFrom', filters.dateNaissanceFrom);
    if (filters.dateNaissanceTo) params = params.set('dateNaissanceTo', filters.dateNaissanceTo);
    if (filters.archived) params = params.set('archived', true);
    if (filters.sansNumeroDossier) params = params.set('sansNumeroDossier', true);
    if (filters.sansCin) params = params.set('sansCin', true);
    if (filters.sort) params = params.set('sort', filters.sort);
    if (filters.direction) params = params.set('direction', filters.direction);
    return this.http.get<PageDTO<Patient>>(this.API_URL, { params });
  }

  getPatientById(id: number): Observable<Patient> {
    return this.http.get<Patient>(`${this.API_URL}/${id}`);
  }

  /**
   * Crée un nouveau patient
   * Nécessite authentification ADMIN
   */
  createPatient(patient: PatientRequest): Observable<Patient> {
    return this.http.post<Patient>(this.API_URL, patient);
  }

  /**
   * Met à jour un patient existant
   * Nécessite authentification ADMIN
   */
  updatePatient(id: number, patient: PatientRequest): Observable<Patient> {
    return this.http.put<Patient>(`${this.API_URL}/${id}`, patient);
  }

  /**
   * Archive un patient (soft delete) : il n'apparaît plus dans les listes par défaut
   * mais ses données médicales (dossier, consultations) sont conservées.
   * DELETE /api/patients/{id} = archivage côté backend, jamais une suppression réelle.
   * Nécessite authentification ADMIN ou MEDECIN référent.
   */
  archivePatient(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  /**
   * Restaure un patient archivé : réapparaît dans les listes par défaut.
   * PUT /api/patients/{id}/restore côté backend : PAS de PATCH .../restore.
   * Nécessite authentification ADMIN
   */
  restorePatient(id: number): Observable<void> {
    return this.http.put<void>(`${this.API_URL}/${id}/restore`, {});
  }
}
