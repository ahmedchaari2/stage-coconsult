import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { IconService } from '@ant-design/icons-angular';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { BehaviorSubject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from 'src/app/core/services/auth.service';
import { MedecinService } from 'src/app/core/services/medecin.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { PatientService } from 'src/app/core/services/patient.service';
import { PatientDetailComponent } from './patient-detail.component';
import { TranslocoService } from '@jsverse/transloco';

describe('PatientDetailComponent consultation target feedback', () => {
  let fixture: ComponentFixture<PatientDetailComponent>;
  let queryParams$: BehaviorSubject<ParamMap>;
  const navigate = vi.fn(() => Promise.resolve(true));
  const showWarning = vi.fn();
  const requestedConsultationMessage = 'The requested consultation could not be displayed.';

  beforeEach(async () => {
    navigate.mockClear();
    showWarning.mockClear();
    queryParams$ = new BehaviorSubject(convertToParamMap({}));

    await TestBed.configureTestingModule({
      imports: [PatientDetailComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ id: 7 })),
            queryParamMap: queryParams$.asObservable()
          }
        },
        { provide: Router, useValue: { navigate } },
        {
          provide: PatientService,
          useValue: { getPatientById: () => of({ id: 7, nom: 'Martin', prenom: 'Alice', archived: false }) }
        },
        { provide: MedecinService, useValue: { getAllMedecinOptions: () => of([]) } },
        { provide: NotificationService, useValue: { showWarning } },
        { provide: AuthService, useValue: { currentUser: signal({ id: 1, role: 'ADMIN' }) } },
        { provide: NgbModal, useValue: {} },
        { provide: IconService, useValue: { addIcon: vi.fn() } },
        {
          provide: TranslocoService,
          useValue: {
            translate: (key: string) =>
              key === 'medicalRecord.errors.requestedConsultationUnavailable' ? requestedConsultationMessage : key
          }
        }
      ]
    })
      .overrideComponent(PatientDetailComponent, {
        set: { template: '@if (patient()) { <div id="patient-dossier">Patient dossier</div> }' }
      })
      .compileComponents();
  });

  function createComponent(queryParams: Record<string, string>): void {
    queryParams$.next(convertToParamMap(queryParams));
    fixture = TestBed.createComponent(PatientDetailComponent);
    fixture.detectChanges();
  }

  it('warns once for an invalid ID, keeps the dossier rendered, and removes only consultationId', () => {
    createComponent({ consultationId: 'invalid', preserved: 'yes' });
    queryParams$.next(convertToParamMap({ consultationId: 'invalid', preserved: 'yes' }));

    expect(showWarning).toHaveBeenCalledTimes(1);
    expect(showWarning).toHaveBeenCalledWith(requestedConsultationMessage);
    expect(fixture.nativeElement.querySelector('#patient-dossier')).not.toBeNull();
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: { consultationId: null },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  });

  it('uses the same one-shot cleanup for an inaccessible or mismatched consultation', () => {
    createComponent({ consultationId: '77', preserved: 'yes' });

    fixture.componentInstance.onTargetConsultationFailed();
    fixture.componentInstance.onTargetConsultationFailed();

    expect(showWarning).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.patient()?.id).toBe(7);
  });

  it('leaves a valid successful deep link intact', () => {
    createComponent({ consultationId: '77', preserved: 'yes' });

    expect(fixture.componentInstance.targetConsultationId()).toBe(77);
    expect(fixture.componentInstance.activeTab()).toBe('record');
    expect(showWarning).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('restores the selected information section from the URL', () => {
    createComponent({ section: 'information' });

    expect(fixture.componentInstance.activeTab()).toBe('information');
  });

  it('opens ordinary patient detail navigation on the information section', () => {
    createComponent({});

    expect(fixture.componentInstance.activeTab()).toBe('information');
  });

  it('keeps a consultation deep link on the medical-record section', () => {
    createComponent({ section: 'information', consultationId: '77' });

    expect(fixture.componentInstance.activeTab()).toBe('record');
    expect(fixture.componentInstance.targetConsultationId()).toBe(77);
  });

  it('keeps the start-consultation flow on the medical-record section after URL cleanup', () => {
    createComponent({ openConsultation: '1', appointmentId: '42', motif: 'Follow-up', date: '2026-07-28' });

    expect(fixture.componentInstance.activeTab()).toBe('record');
    expect(fixture.componentInstance.consultationPrefill()?.appointmentId).toBe(42);
    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: { section: 'record' },
      replaceUrl: true
    });
  });

  it('writes tab changes to the URL and clears an obsolete consultation target', () => {
    createComponent({ consultationId: '77' });

    fixture.componentInstance.setActiveTab('history');

    expect(fixture.componentInstance.activeTab()).toBe('history');
    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: { section: 'history', consultationId: null },
      queryParamsHandling: 'merge'
    });
  });

  it('preserves a consultation target when selecting the medical-record tab', () => {
    createComponent({ consultationId: '77' });

    fixture.componentInstance.setActiveTab('record');

    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: TestBed.inject(ActivatedRoute),
      queryParams: { section: 'record' },
      queryParamsHandling: 'merge'
    });
  });
});
