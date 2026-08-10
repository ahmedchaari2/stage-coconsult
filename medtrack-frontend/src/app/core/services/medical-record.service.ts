import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  MedicalRecord,
  MedicalRecordRequest,
  Consultation,
  ConsultationRequest,
  ConsultationFilters,
  ConsultationExists
} from '../models/medical-record.model';
import { PageDTO } from '../models/page.model';
import { environment } from 'src/environments/environment';

@Injectable({
  providedIn: 'root'
})
export class MedicalRecordService {
  private readonly PATIENTS_URL = `${environment.apiUrl}/patients`;
  private readonly RECORDS_URL = `${environment.apiUrl}/medical-records`;
  private readonly CONSULTATIONS_URL = `${environment.apiUrl}/consultations`;
  private readonly http = inject(HttpClient);

  /**
   * Récupère le dossier médical d'un patient (GET /api/patients/{patientId}/medical-record).
   * 404 si le patient n'a pas encore de dossier : à l'appelant de traiter ce cas
   * comme l'état vide plutôt que comme une erreur.
   */
  getByPatient(patientId: number): Observable<MedicalRecord> {
    return this.http.get<MedicalRecord>(`${this.PATIENTS_URL}/${patientId}/medical-record`);
  }

  create(patientId: number, request: MedicalRecordRequest): Observable<MedicalRecord> {
    return this.http.post<MedicalRecord>(`${this.PATIENTS_URL}/${patientId}/medical-record`, request);
  }

  /**
   * Met à jour le dossier médical (PUT /api/patients/{patientId}/medical-record —
   * même ressource que get/create, PAS /api/medical-records/{id} qui n'existe pas
   * pour cette opération côté backend : MedicalRecordController n'expose que
   * GET/POST/PUT sous /api/patients/{patientId}/medical-record).
   */
  update(patientId: number, request: MedicalRecordRequest): Observable<MedicalRecord> {
    return this.http.put<MedicalRecord>(`${this.PATIENTS_URL}/${patientId}/medical-record`, request);
  }

  /**
   * Récupère une page de consultations d'un dossier
   * (GET /api/medical-records/{id}/consultations?page=&size=&motif=&medecinId=&dateFrom=&dateTo=&q=&sort=&direction=).
   */
  getConsultations(recordId: number, page = 0, size = 10, filters?: ConsultationFilters): Observable<PageDTO<Consultation>> {
    let params = new HttpParams().set('page', page).set('size', size);

    if (filters?.motif) {
      params = params.set('motif', filters.motif);
    }
    if (filters?.medecinId != null) {
      params = params.set('medecinId', filters.medecinId);
    }
    if (filters?.dateFrom) {
      params = params.set('dateFrom', filters.dateFrom);
    }
    if (filters?.dateTo) {
      params = params.set('dateTo', filters.dateTo);
    }
    if (filters?.archived) {
      params = params.set('archived', true);
    }
    if (filters?.q) {
      params = params.set('q', filters.q);
    }
    if (filters?.sort) {
      params = params.set('sort', filters.sort);
    }
    if (filters?.direction) {
      params = params.set('direction', filters.direction);
    }

    return this.http.get<PageDTO<Consultation>>(`${this.RECORDS_URL}/${recordId}/consultations`, { params });
  }

  getConsultationById(consultationId: number): Observable<Consultation> {
    return this.http.get<Consultation>(`${this.CONSULTATIONS_URL}/${consultationId}`);
  }

  createConsultation(recordId: number, request: ConsultationRequest): Observable<Consultation> {
    return this.http.post<Consultation>(`${this.RECORDS_URL}/${recordId}/consultations`, request);
  }

  updateConsultation(consultationId: number, request: ConsultationRequest): Observable<Consultation> {
    return this.http.put<Consultation>(`${this.CONSULTATIONS_URL}/${consultationId}`, request);
  }

  /**
   * Archive une consultation (soft delete) : conservée dans l'historique mais
   * signalée comme archivée. DELETE /api/consultations/{id} côté backend
   * (ConsultationController) : PAS de PATCH .../archive, cet endpoint n'existe pas.
   * Restauration possible via `restoreConsultation` ci-dessous (route backend ajoutée
   * depuis, voir ConsultationController.restore).
   */
  archiveConsultation(consultationId: number): Observable<void> {
    return this.http.delete<void>(`${this.CONSULTATIONS_URL}/${consultationId}`);
  }

  /**
   * Restaure une consultation archivée (PUT /api/consultations/{id}/restore, même pattern
   * que PatientService.restore). Contrôle d'accès ADMIN ou MEDECIN référent (pas ADMIN
   * uniquement comme pour Patient) — voir ConsultationController.restore.
   */
  restoreConsultation(consultationId: number): Observable<void> {
    return this.http.put<void>(`${this.CONSULTATIONS_URL}/${consultationId}/restore`, {});
  }

  /**
   * Indique si un rendez-vous a déjà une consultation (GET /api/consultations/by-appointment/{id}) :
   * lien réciproque rendez-vous -> consultation, et garde-fou contre la double création.
   */
  getConsultationByAppointment(appointmentId: number): Observable<ConsultationExists> {
    return this.http.get<ConsultationExists>(`${this.CONSULTATIONS_URL}/by-appointment/${appointmentId}`);
  }
}
