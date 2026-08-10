import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AccesLog, TypeAction, TypeRessource } from '../models/acces-log.model';
import { PageDTO } from '../models/page.model';
import { environment } from 'src/environments/environment';

export interface AccesLogFilterParams {
  utilisateurId?: number;
  patientId?: number;
  typeRessource?: TypeRessource;
  action?: TypeAction;
  dateFrom?: string; // ISO LocalDateTime
  dateTo?: string; // ISO LocalDateTime
  /** Recherche backend : nom/prénom du patient OU de l'utilisateur concerné par l'accès. */
  q?: string;
  sort?: string;
  direction?: 'asc' | 'desc';
}

@Injectable({
  providedIn: 'root'
})
export class AccesLogService {
  private readonly API_URL = `${environment.apiUrl}/acces-logs`;
  private readonly http = inject(HttpClient);

  getAccesLogs(page = 0, size = 10, filters: AccesLogFilterParams = {}): Observable<PageDTO<AccesLog>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.utilisateurId != null) params = params.set('utilisateurId', filters.utilisateurId);
    if (filters.patientId != null) params = params.set('patientId', filters.patientId);
    if (filters.typeRessource) params = params.set('typeRessource', filters.typeRessource);
    if (filters.action) params = params.set('action', filters.action);
    if (filters.dateFrom) params = params.set('dateFrom', filters.dateFrom);
    if (filters.dateTo) params = params.set('dateTo', filters.dateTo);
    if (filters.q) params = params.set('q', filters.q);
    if (filters.sort) params = params.set('sort', filters.sort);
    if (filters.direction) params = params.set('direction', filters.direction);
    return this.http.get<PageDTO<AccesLog>>(this.API_URL, { params });
  }
}
