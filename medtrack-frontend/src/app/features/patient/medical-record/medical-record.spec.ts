import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Observable, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Consultation, MedicalRecord as MedicalRecordDto } from 'src/app/core/models/medical-record.model';
import { AppointmentService } from 'src/app/core/services/appointment.service';
import { AuthService } from 'src/app/core/services/auth.service';
import { MedicalRecordService } from 'src/app/core/services/medical-record.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { MedicalRecord } from './medical-record';

const record: MedicalRecordDto = {
  id: 12,
  patientId: 7,
  groupeSanguin: 'A_POSITIF',
  allergies: 'Aucune',
  antecedents: 'Aucun'
};

const consultation: Consultation = {
  id: 77,
  medicalRecordId: 12,
  medecinId: 4,
  appointmentId: 31,
  consultationDate: '2026-07-28',
  motif: 'Contrôle',
  diagnostic: 'Stable',
  notes: null
};

describe('MedicalRecord consultation deep-link', () => {
  let fixture: ComponentFixture<MedicalRecord>;
  let consultationResponse$: Observable<Consultation>;
  const getConsultationById = vi.fn(() => consultationResponse$);

  beforeEach(async () => {
    getConsultationById.mockClear();
    consultationResponse$ = of(consultation);
    await TestBed.configureTestingModule({
      imports: [
        MedicalRecord,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' }
        })
      ],
      providers: [
        provideRouter([]),
        {
          provide: MedicalRecordService,
          useValue: {
            getByPatient: () => of(record),
            getConsultations: () => of({ content: [], page: 0, size: 10, totalPages: 0, totalElements: 0 }),
            getConsultationById
          }
        },
        { provide: AppointmentService, useValue: {} },
        { provide: NotificationService, useValue: {} },
        {
          provide: AuthService,
          useValue: { currentUser: signal({ id: 4, role: 'MEDECIN', nom: 'Bernard', prenom: 'Sophie', email: 'doctor@medtrack.test' }) }
        }
      ]
    }).compileComponents();
  });

  async function createComponent(): Promise<void> {
    fixture = TestBed.createComponent(MedicalRecord);
    fixture.componentRef.setInput('patientId', 7);
    fixture.componentRef.setInput('medecins', []);
    fixture.componentRef.setInput('targetConsultationId', 77);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('loads, renders, and highlights the targeted consultation without making its row focusable', async () => {
    await createComponent();

    const row = fixture.nativeElement.querySelector('#consultation-77') as HTMLTableRowElement;
    expect(getConsultationById).toHaveBeenCalledWith(77);
    expect(row).not.toBeNull();
    expect(row.classList.contains('table-active')).toBe(true);
    expect(row.getAttribute('aria-current')).toBe('true');
    expect(row.hasAttribute('tabindex')).toBe(false);
    expect(document.activeElement).not.toBe(row);
    expect(row.cells[0].querySelector('button')).toBeNull();
    expect((row.querySelector('a') as HTMLAnchorElement).getAttribute('href')).toBe('/patients/7?consultationId=77');
  });

  it('provides internal navigation to the medical summary and consultations', async () => {
    await createComponent();

    const buttons = Array.from(fixture.nativeElement.querySelectorAll('.medical-record-section-nav button')) as HTMLButtonElement[];
    expect(buttons).toHaveLength(2);
    expect(buttons.every((button) => button.type === 'button')).toBe(true);
    expect(buttons.map((button) => button.getAttribute('aria-controls'))).toEqual([
      'medical-record-summary',
      'medical-record-consultations'
    ]);
    expect(fixture.nativeElement.querySelector('.medical-record-section-nav a')).toBeNull();
    expect(buttons[0].getAttribute('aria-selected')).toBe('false');
    expect(buttons[1].getAttribute('aria-selected')).toBe('true');
    expect(fixture.nativeElement.querySelector('#medical-record-summary')).toBeNull();
    expect(fixture.nativeElement.querySelector('#medical-record-consultations')).not.toBeNull();

    buttons[0].click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#medical-record-summary')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#medical-record-consultations')).toBeNull();
  });

  it('reports an inaccessible or missing consultation once and keeps the dossier rendered', async () => {
    consultationResponse$ = throwError(() => ({ status: 404 }));
    await createComponent();
    let failures = 0;
    fixture.componentInstance.targetConsultationFailed.subscribe(() => failures++);

    // Re-issue the target after subscribing; the component remains usable after the failed GET.
    fixture.componentRef.setInput('targetConsultationId', null);
    fixture.detectChanges();
    fixture.componentRef.setInput('targetConsultationId', 77);
    fixture.detectChanges();

    expect(failures).toBe(1);
    expect(fixture.componentInstance.record()).toEqual(record);
    expect(fixture.nativeElement.textContent).toContain('A+');
  });

  it('reports a medical-record mismatch without rendering the targeted consultation', async () => {
    consultationResponse$ = of({ ...consultation, medicalRecordId: 999 });
    await createComponent();
    let failures = 0;
    fixture.componentInstance.targetConsultationFailed.subscribe(() => failures++);

    fixture.componentRef.setInput('targetConsultationId', null);
    fixture.detectChanges();
    fixture.componentRef.setInput('targetConsultationId', 77);
    fixture.detectChanges();

    expect(failures).toBe(1);
    expect(fixture.nativeElement.querySelector('#consultation-77')).toBeNull();
    expect(fixture.componentInstance.record()).toEqual(record);
  });
});
