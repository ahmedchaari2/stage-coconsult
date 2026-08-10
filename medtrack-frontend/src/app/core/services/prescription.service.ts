import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';
import { Prescription, PrescriptionRequest, PrescriptionFilters } from '../models/prescription.model';
import { PageDTO } from '../models/page.model';
import { environment } from 'src/environments/environment';

/** Corps d'erreur relu depuis un Blob : JSON { error } du backend, ou le texte brut à défaut. */
function parseErrorBody(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

@Injectable({
  providedIn: 'root'
})
export class PrescriptionService {
  private readonly CONSULTATIONS_URL = `${environment.apiUrl}/consultations`;
  private readonly PRESCRIPTIONS_URL = `${environment.apiUrl}/prescriptions`;
  private readonly http = inject(HttpClient);

  /**
   * Liste non paginée des prescriptions d'une consultation
   * (GET /api/consultations/{consultationId}/prescriptions?archived=). Filtre exact côté
   * backend (pas "inclusif") : archived=true renvoie UNIQUEMENT les archivées, jamais
   * archivées + actives — même contrat que ConsultationFilters.archived.
   */
  listForConsultation(consultationId: number, archived = false): Observable<Prescription[]> {
    let params = new HttpParams();
    if (archived) {
      params = params.set('archived', true);
    }
    return this.http.get<Prescription[]>(`${this.CONSULTATIONS_URL}/${consultationId}/prescriptions`, { params });
  }

  /** Crée une prescription dans une consultation (POST /api/consultations/{consultationId}/prescriptions). */
  create(consultationId: number, request: PrescriptionRequest, renewal = false): Observable<Prescription> {
    const params = renewal ? new HttpParams().set('renewal', true) : undefined;
    return this.http.post<Prescription>(`${this.CONSULTATIONS_URL}/${consultationId}/prescriptions`, request, { params });
  }

  /**
   * Recherche paginée transversale, tous patients confondus (GET /api/prescriptions).
   * Le backend restreint automatiquement un MEDECIN aux prescriptions des consultations de
   * ses patients référents (PrescriptionService.buildScopedSpec) : aucun filtrage de portée
   * à ajouter côté client.
   */
  search(page = 0, size = 10, filters?: PrescriptionFilters): Observable<PageDTO<Prescription>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters?.patientId != null) {
      params = params.set('patientId', filters.patientId);
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
    if (filters?.statutCalcule) {
      params = params.set('statutCalcule', filters.statutCalcule);
    }
    if (filters?.sort) {
      params = params.set('sort', filters.sort);
    }
    if (filters?.direction) {
      params = params.set('direction', filters.direction);
    }
    return this.http.get<PageDTO<Prescription>>(this.PRESCRIPTIONS_URL, { params });
  }

  /** Lecture explicite d'une prescription ; cet endpoint individuel est journalisé côté serveur. */
  getById(id: number): Observable<Prescription> {
    return this.http.get<Prescription>(`${this.PRESCRIPTIONS_URL}/${id}`);
  }

  /**
   * Ordonnance PDF des prescriptions actives d'une consultation
   * (GET /api/consultations/{consultationId}/ordonnance).
   *
   * Réponse binaire, donc le corps d'une erreur arrive lui aussi en Blob : sans le re-décoder,
   * extractBackendErrorMessage ne verrait qu'un objet opaque et le message du backend serait
   * perdu au profit d'un libellé générique.
   */
  downloadOrdonnance(consultationId: number): Observable<Blob> {
    return this.http.get(`${this.CONSULTATIONS_URL}/${consultationId}/ordonnance`, { responseType: 'blob' }).pipe(
      catchError((err: HttpErrorResponse) => {
        if (!(err.error instanceof Blob)) {
          return throwError(() => err);
        }
        return from(err.error.text()).pipe(
          switchMap((text) =>
            throwError(
              () =>
                new HttpErrorResponse({
                  error: parseErrorBody(text),
                  status: err.status,
                  statusText: err.statusText,
                  url: err.url ?? undefined
                })
            )
          )
        );
      })
    );
  }

  update(id: number, request: PrescriptionRequest): Observable<Prescription> {
    return this.http.put<Prescription>(`${this.PRESCRIPTIONS_URL}/${id}`, request);
  }

  /** Archive une prescription (soft delete, DELETE /api/prescriptions/{id}) : donnée médicale, jamais supprimée physiquement. */
  archive(id: number): Observable<void> {
    return this.http.delete<void>(`${this.PRESCRIPTIONS_URL}/${id}`);
  }

  /** Restaure une prescription archivée (PUT /api/prescriptions/{id}/restore). */
  restore(id: number): Observable<Prescription> {
    return this.http.put<Prescription>(`${this.PRESCRIPTIONS_URL}/${id}/restore`, {});
  }
}
