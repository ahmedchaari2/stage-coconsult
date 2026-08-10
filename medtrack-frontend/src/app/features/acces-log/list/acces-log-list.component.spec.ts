import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AccesLogService } from 'src/app/core/services/acces-log.service';
import { AuthService } from 'src/app/core/services/auth.service';
import { MedecinService } from 'src/app/core/services/medecin.service';
import { PatientService } from 'src/app/core/services/patient.service';
import { AccesLogListComponent } from './acces-log-list.component';

describe('AccesLogListComponent interaction contract', () => {
  let fixture: ComponentFixture<AccesLogListComponent>;
  let getPatientById: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    getPatientById = vi.fn(() => of({ id: 7, nom: 'Martin', prenom: 'Alice' }));
    await TestBed.configureTestingModule({
      imports: [
        AccesLogListComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' }
        })
      ],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } }
        },
        {
          provide: AccesLogService,
          useValue: {
            getAccesLogs: () =>
              of({
                content: [
                  {
                    id: 1,
                    utilisateurId: 2,
                    typeRessource: 'PATIENT',
                    action: 'UPDATE',
                    ressourceId: 7,
                    patientId: 7,
                    patientNom: 'Martin',
                    patientPrenom: 'Alice',
                    dateHeure: '2026-07-28T10:30:00'
                  }
                ],
                page: 0,
                size: 10,
                totalPages: 1,
                totalElements: 1
              })
          }
        },
        { provide: MedecinService, useValue: { getAllMedecinOptions: () => of([]) } },
        { provide: PatientService, useValue: { getPatientById } },
        {
          provide: AuthService,
          useValue: { currentUser: signal({ id: 2, role: 'ADMIN', nom: 'Admin', prenom: 'MedTrack' }) }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AccesLogListComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('keeps access-log rows list-only with no View action or navigation', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate');
    const navigateByUrl = vi.spyOn(router, 'navigateByUrl');
    const row = fixture.nativeElement.querySelector('tbody tr') as HTMLTableRowElement;

    row.click();
    row.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));

    expect(row.hasAttribute('role')).toBe(false);
    expect(row.hasAttribute('tabindex')).toBe(false);
    expect(row.classList.contains('cursor-pointer')).toBe(false);
    expect(row.querySelector('a, button, i[type="eye"]')).toBeNull();
    expect(navigate).not.toHaveBeenCalled();
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('uses patient names embedded in the audit response without a detail request', () => {
    expect(fixture.nativeElement.querySelector('tbody tr td:last-child')?.textContent).toContain('Martin Alice');
    expect(getPatientById).not.toHaveBeenCalled();
  });

  it('offers every backend resource type in the filter', () => {
    const component = fixture.componentInstance;
    expect(component.typeRessourceOptions).toEqual([
      'PATIENT',
      'MEDECIN',
      'RENDEZ_VOUS',
      'DOSSIER_MEDICAL',
      'CONSULTATION',
      'PRESCRIPTION'
    ]);
  });

  it('offers every supported audit action in the filter', () => {
    expect(fixture.componentInstance.actionOptions).toEqual([
      'VIEW',
      'CREATE',
      'UPDATE',
      'ARCHIVE',
      'RESTORE',
      'DELETE',
      'PRINT',
      'RENEW',
      'STATUS_CHANGE'
    ]);
  });

  it('shows no patient for resources that are not patient-linked', () => {
    expect(
      fixture.componentInstance.resourceDisplay({
        id: 2,
        utilisateurId: 2,
        typeRessource: 'MEDECIN',
        action: 'VIEW',
        ressourceId: 4,
        patientId: null,
        ressourceNom: 'Bernard',
        ressourcePrenom: 'Sophie',
        dateHeure: '2026-07-28T14:30:00'
      })
    ).toBe('Bernard Sophie');
  });
});
