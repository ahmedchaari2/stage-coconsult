import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { Consultation } from '../models/medical-record.model';
import { MedicalRecordService } from './medical-record.service';
import { environment } from 'src/environments/environment';

describe('MedicalRecordService consultation detail', () => {
  let service: MedicalRecordService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(MedicalRecordService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads a consultation by its reliable ID', () => {
    const consultation = { id: 77, medicalRecordId: 12 } as Consultation;
    let result: Consultation | undefined;

    service.getConsultationById(77).subscribe((value) => (result = value));

    const request = http.expectOne(`${environment.apiUrl}/consultations/77`);
    expect(request.request.method).toBe('GET');
    request.flush(consultation);
    expect(result).toEqual(consultation);
  });
});
