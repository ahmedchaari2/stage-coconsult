import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Appointment, AppointmentStatut, CreateAppointmentRequest, UpdateAppointmentRequest } from '../models/appointment.model';
import { PageDTO } from '../models/page.model';
import { environment } from 'src/environments/environment';

/** Filtres optionnels de GET /api/appointments (recherche avancée, voir AppointmentFilter côté backend). */
export interface AppointmentFilterParams {
  patientId?: number;
  medecinId?: number;
  statut?: AppointmentStatut;
  dateFrom?: string; // ISO LocalDateTime
  dateTo?: string; // ISO LocalDateTime
  /** Recherche combinée backend : nom/prénom du patient (OR) OU motif du rendez-vous. */
  q?: string;
  sort?: string;
  direction?: 'asc' | 'desc';
}

@Injectable({
  providedIn: 'root'
})
export class AppointmentService {
  private readonly API_URL = `${environment.apiUrl}/appointments`;
  private readonly http = inject(HttpClient);

  /**
   * Liste paginée transversale, triée par dateHeure ASC par défaut (personnalisable via
   * filters.sort/direction). ADMIN : accès global. MEDECIN : restreint automatiquement à ses
   * patients référents ou aux rendez-vous qui lui sont assignés par le backend
   * (AppointmentService.buildScopedSpec), aucun filtre à ajouter ici
   * pour ça.
   */
  getAppointments(page = 0, size = 10, filters: AppointmentFilterParams = {}): Observable<PageDTO<Appointment>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.patientId != null) params = params.set('patientId', filters.patientId);
    if (filters.medecinId != null) params = params.set('medecinId', filters.medecinId);
    if (filters.statut) params = params.set('statut', filters.statut);
    if (filters.dateFrom) params = params.set('dateFrom', filters.dateFrom);
    if (filters.dateTo) params = params.set('dateTo', filters.dateTo);
    if (filters.q) params = params.set('q', filters.q);
    if (filters.sort) params = params.set('sort', filters.sort);
    if (filters.direction) params = params.set('direction', filters.direction);
    return this.http.get<PageDTO<Appointment>>(this.API_URL, { params });
  }

  /**
   * Rendez-vous d'un mois donné pour la vue calendrier (GET /api/appointments/calendar?month=YYYY-MM).
   * Non paginé (volume borné à un mois). Même scope de sécurité que getAppointments.
   */
  getCalendar(month: string): Observable<Appointment[]> {
    const params = new HttpParams().set('month', month);
    return this.http.get<Appointment[]>(`${this.API_URL}/calendar`, { params });
  }

  /**
   * Créneaux libres d'un médecin pour une journée, au format "HH:mm"
   * (GET /api/appointments/disponibilites?medecinId=X&date=YYYY-MM-DD). Durée de créneau et
   * heures d'ouverture sont fixées côté backend (config-server). medecinId est ignoré si
   * l'appelant est MEDECIN (forcé à lui-même), obligatoire pour un ADMIN.
   */
  getDisponibilites(date: string, medecinId?: number | null): Observable<string[]> {
    let params = new HttpParams().set('date', date);
    if (medecinId != null) params = params.set('medecinId', medecinId);
    return this.http.get<string[]>(`${this.API_URL}/disponibilites`, { params });
  }

  getAppointmentById(id: number): Observable<Appointment> {
    return this.http.get<Appointment>(`${this.API_URL}/${id}`);
  }

  createAppointment(request: CreateAppointmentRequest): Observable<Appointment> {
    return this.http.post<Appointment>(this.API_URL, request);
  }

  updateAppointment(id: number, request: UpdateAppointmentRequest): Observable<Appointment> {
    return this.http.put<Appointment>(`${this.API_URL}/${id}`, request);
  }

  deleteAppointment(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }
}
