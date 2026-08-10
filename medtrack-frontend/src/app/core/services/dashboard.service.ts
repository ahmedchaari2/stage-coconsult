import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AdminDashboard, AujourdhuiResponse, MedecinDashboard } from '../models/dashboard.model';
import { environment } from 'src/environments/environment';

/**
 * Tableau de bord agrégé (dashboard-service). Deux vues selon le rôle : le backend scope
 * automatiquement la vue médecin au praticien connecté (identité repropagée en Feign).
 */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly API_URL = `${environment.apiUrl}/dashboard`;
  private readonly http = inject(HttpClient);

  /** Vue du médecin connecté (rôle MEDECIN). */
  getMedecinDashboard(): Observable<MedecinDashboard> {
    return this.http.get<MedecinDashboard>(`${this.API_URL}/medecin`);
  }

  /**
   * Vue de supervision transversale (rôle ADMIN). periode ("7j"/"30j"/"mois") ne pilote que la
   * carte activité par médecin côté backend, voir DashboardService.adminDashboard.
   */
  getAdminDashboard(periode: '7j' | '30j' | 'mois' = 'mois'): Observable<AdminDashboard> {
    const params = new HttpParams().set('periode', periode);
    return this.http.get<AdminDashboard>(`${this.API_URL}/admin`, { params });
  }

  /** Journée en cours (ADMIN ou MEDECIN, scope appliqué côté backend). */
  getAujourdhui(): Observable<AujourdhuiResponse> {
    return this.http.get<AujourdhuiResponse>(`${this.API_URL}/aujourdhui`);
  }
}
