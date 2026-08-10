import { signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Prescription } from 'src/app/core/models/prescription.model';
import { PrescriptionService } from 'src/app/core/services/prescription.service';
import { PatientService } from 'src/app/core/services/patient.service';
import { MedecinService } from 'src/app/core/services/medecin.service';
import { MedicalRecordService } from 'src/app/core/services/medical-record.service';
import { AuthService } from 'src/app/core/services/auth.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { PrescriptionListComponent } from './prescription-list.component';
import { PrescriptionCreateWizardComponent } from '../create-wizard/prescription-create-wizard.component';

const prescription: Prescription = {
  id: 19,
  consultationId: 31,
  medicament: 'Amoxicilline',
  posologie: 'Deux fois par jour',
  dureeJours: 7,
  instructions: null,
  renouvelable: true,
  archived: false,
  consultationDate: '2026-07-28',
  patientId: 7,
  patientNom: 'Martin',
  patientPrenom: 'Alice',
  medecinId: 4,
  medecinNom: 'Bernard',
  medecinPrenom: 'Sophie'
};

describe('PrescriptionListComponent navigation', () => {
  let fixture: ComponentFixture<PrescriptionListComponent>;
  let component: PrescriptionListComponent;
  let router: Router;
  const getById = vi.fn(() => of(prescription));

  beforeEach(async () => {
    getById.mockClear();
    await TestBed.configureTestingModule({
      imports: [
        PrescriptionListComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            en: {
              prescription: {
                view: { title: 'Prescription details' },
                fields: { medicament: 'Medication', posologie: 'Dosage', dureeJours: 'Duration', renouvelable: 'Renewable' },
                badges: { renouvelable: 'Renewable' },
                ordonnance: { tooltip: 'Print', archivedTooltip: 'Print unavailable' },
                list: {
                  title: 'Prescriptions',
                  addButton: 'New prescription',
                  renewTooltip: 'Renew',
                  joursUnit: 'days',
                  viewPatientRow: 'View patient {{ nom }} {{ prenom }}',
                  viewMedecinRow: 'View doctor {{ nom }} {{ prenom }}',
                  columns: { patient: 'Patient', medecin: 'Doctor', consultationDate: 'Consultation', statutCalcule: 'Status' },
                  statut: { active: 'Active', expiree: 'Expired' },
                  filters: { searchPlaceholder: 'Search' }
                }
              },
              common: {
                loading: 'Loading',
                labels: { actions: 'Actions', archived: 'Archived' },
                buttons: {
                  view: 'View',
                  close: 'Close',
                  edit: 'Edit',
                  archive: 'Archive',
                  restore: 'Restore',
                  previous: 'Previous',
                  next: 'Next'
                }
              },
              pagination: { show: 'Show', perPage: 'per page', noItems: 'No items', of: 'of', page: 'Page', item: 'item', items: 'items' }
            }
          },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' }
        })
      ],
      providers: [
        provideRouter([]),
        {
          provide: PrescriptionService,
          useValue: {
            search: () => of({ content: [prescription], page: 0, size: 10, totalPages: 1, totalElements: 1 }),
            getById
          }
        },
        { provide: PatientService, useValue: {} },
        { provide: MedecinService, useValue: {} },
        {
          provide: MedicalRecordService,
          useValue: {
            getByPatient: () => of({ id: 12 }),
            getConsultations: () => of({ content: [] })
          }
        },
        { provide: AuthService, useValue: { currentUser: signal({ id: 4, role: 'MEDECIN', nom: 'Bernard', prenom: 'Sophie' }) } },
        { provide: NotificationService, useValue: {} }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PrescriptionListComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('renders the medication as plain emphasized text and does not make the row interactive', () => {
    const row = fixture.nativeElement.querySelector('tbody tr') as HTMLTableRowElement;
    const medicationCell = row.querySelector('td:first-child') as HTMLTableCellElement;

    expect(medicationCell.textContent).toContain('Amoxicilline');
    expect(medicationCell.querySelector('a, button')).toBeNull();
    expect(row.classList.contains('cursor-pointer')).toBe(false);
    expect(row.hasAttribute('role')).toBe(false);
    expect(row.hasAttribute('tabindex')).toBe(false);
  });

  it('links the patient name to the correct patient ID', () => {
    const patientLink = fixture.nativeElement.querySelector('tbody td:nth-child(3) a.table-record-link') as HTMLAnchorElement;

    expect(patientLink.textContent).toContain('Martin Alice');
    expect(patientLink.getAttribute('href')).toBe('/patients/7');
  });

  it('hides the doctor column entirely for a MEDECIN, who only ever sees their own prescriptions', () => {
    const headerCells = Array.from(fixture.nativeElement.querySelectorAll('thead th')) as HTMLTableCellElement[];
    const row = fixture.nativeElement.querySelector('tbody tr') as HTMLTableRowElement;

    expect(headerCells.some((th) => th.textContent?.includes('Doctor'))).toBe(false);
    expect(row.textContent).not.toContain('Bernard Sophie');
  });

  it('links the doctor name with the backend-provided ID for an admin', () => {
    TestBed.inject(AuthService).currentUser.set({ id: 1, role: 'ADMIN', nom: 'Admin', prenom: 'MedTrack', email: 'admin@medtrack.test' });
    fixture.detectChanges();

    const doctorLink = fixture.nativeElement.querySelector('tbody td:nth-child(4) a.table-record-link') as HTMLAnchorElement;
    expect(doctorLink.getAttribute('href')).toBe('/medecins/4');
  });

  it('loads exactly one prescription by ID before opening the explicit View modal', () => {
    const modalService = (component as unknown as { modalService: NgbModal }).modalService;
    const open = vi.spyOn(modalService, 'open').mockReturnValue({} as ReturnType<NgbModal['open']>);

    component.onView(prescription);

    expect(getById).toHaveBeenCalledWith(19);
    expect(component.viewedPrescription).toBe(prescription);
    expect(component.viewLoadingId()).toBeNull();
    expect(open).toHaveBeenCalled();
  });

  it('opens the creation and renewal wizard with a dismissible backdrop', () => {
    const wizard = fixture.debugElement.query(By.directive(PrescriptionCreateWizardComponent))
      .componentInstance as PrescriptionCreateWizardComponent;
    const modalService = (wizard as unknown as { modalService: NgbModal }).modalService;
    const open = vi.spyOn(modalService, 'open').mockReturnValue({} as ReturnType<NgbModal['open']>);

    component.openCreateWizard();

    expect(open).toHaveBeenCalledWith(wizard.wizardModalRef, { centered: true, backdrop: true, size: 'lg' });

    component.onRenew(prescription);

    expect(open).toHaveBeenLastCalledWith(wizard.wizardModalRef, { centered: true, backdrop: true, size: 'lg' });
  });

  it('keeps all prescription actions explicit and separate from navigation', () => {
    const navigate = vi.spyOn(router, 'navigate');
    const navigateByUrl = vi.spyOn(router, 'navigateByUrl');
    const print = vi.spyOn(component, 'onPrintOrdonnance').mockImplementation(() => undefined);
    const view = vi.spyOn(component, 'onView').mockImplementation(() => undefined);
    const renew = vi.spyOn(component, 'onRenew').mockImplementation(() => undefined);
    const edit = vi.spyOn(component, 'onEdit').mockImplementation(() => undefined);
    const archive = vi.spyOn(component, 'onArchiveToggle').mockImplementation(() => undefined);
    const actionButtons = fixture.nativeElement.querySelectorAll('tbody td:last-child button') as NodeListOf<HTMLButtonElement>;

    actionButtons.forEach((button) => button.click());

    expect([...actionButtons].every((button) => button.type === 'button' && button.getAttribute('aria-label'))).toBe(true);
    expect(print).toHaveBeenCalledWith(prescription);
    expect(view).toHaveBeenCalledWith(prescription);
    expect(renew).toHaveBeenCalledWith(prescription);
    expect(edit).toHaveBeenCalledWith(prescription);
    expect(archive).toHaveBeenCalledWith(prescription);
    expect(navigate).not.toHaveBeenCalled();
    expect(navigateByUrl).not.toHaveBeenCalled();
  });
});
