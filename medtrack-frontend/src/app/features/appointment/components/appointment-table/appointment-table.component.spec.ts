import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Appointment } from 'src/app/core/models/appointment.model';
import { Pagination } from 'src/app/theme/shared/components/pagination/pagination';
import { AppointmentTableComponent } from './appointment-table.component';

const plannedAppointment: Appointment = {
  id: 31,
  patientId: 7,
  patientNom: 'Martin',
  patientPrenom: 'Alice',
  medecinId: 4,
  medecinNom: 'Bernard',
  medecinPrenom: 'Sophie',
  dateHeure: '2026-07-28T10:30:00',
  motif: 'Contrôle annuel',
  statut: 'PLANIFIE',
  notes: 'Apporter les analyses'
};

const honoredAppointment: Appointment = {
  ...plannedAppointment,
  id: 32,
  statut: 'HONORE',
  notes: null
};

const confirmedAppointment: Appointment = {
  ...plannedAppointment,
  id: 33,
  statut: 'CONFIRME'
};

const cancelledAppointment: Appointment = {
  ...plannedAppointment,
  id: 34,
  statut: 'ANNULE'
};

describe('AppointmentTableComponent', () => {
  let fixture: ComponentFixture<AppointmentTableComponent>;
  let component: AppointmentTableComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        AppointmentTableComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            en: {
              appointment: {
                statut: {
                  PLANIFIE: 'Planned',
                  CONFIRME: 'Confirmed',
                  ANNULE: 'Cancelled',
                  HONORE: 'Completed'
                },
                list: {
                  columns: {
                    patient: 'Patient',
                    medecin: 'Doctor',
                    dateHeure: 'Date and time',
                    motif: 'Reason',
                    statut: 'Status',
                    notes: 'Notes'
                  },
                  viewPatientRow: 'View patient {{ nom }} {{ prenom }}',
                  empty: { title: 'No appointments', text: 'No appointment is available.' },
                  quickActions: {
                    confirm: 'Confirm',
                    honor: 'Mark completed',
                    cancel: 'Cancel appointment',
                    startConsultation: 'Start consultation',
                    viewConsultation: 'View consultation'
                  }
                }
              },
              common: {
                loading: 'Loading',
                labels: { actions: 'Actions' },
                buttons: { retry: 'Retry', view: 'View', edit: 'Edit', delete: 'Delete', previous: 'Previous', next: 'Next' }
              },
              pagination: {
                show: 'Show',
                perPage: 'per page',
                noItems: 'No items',
                of: 'of',
                page: 'Page',
                item: 'item',
                items: 'items'
              }
            }
          },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' }
        })
      ],
      providers: [provideRouter([])]
    }).compileComponents();

    fixture = TestBed.createComponent(AppointmentTableComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('appointments', [plannedAppointment]);
    fixture.componentRef.setInput('canStartConsultation', () => false);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('renders appointment data and notes', () => {
    fixture.componentRef.setInput('canViewMedecin', true);
    fixture.detectChanges();

    const row = fixture.nativeElement.querySelector('tbody tr') as HTMLTableRowElement;

    expect(row.textContent).toContain('Martin Alice');
    expect(row.textContent).toContain('Bernard Sophie');
    expect(row.textContent).toContain('Contrôle annuel');
    expect(row.textContent).toContain('Planned');
    expect(row.querySelector('[aria-label="Apporter les analyses"]')).not.toBeNull();
  });

  it('renders and marks the highlighted appointment', () => {
    fixture.componentRef.setInput('highlightedAppointmentId', plannedAppointment.id);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('tbody tr')?.classList.contains('table-active')).toBe(true);
  });

  it('renders the loading state without the table', () => {
    fixture.componentRef.setInput('isLoading', true);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="status"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('renders the error state and emits retry', () => {
    let retries = 0;
    component.retryRequested.subscribe(() => retries++);
    fixture.componentRef.setInput('error', 'Unable to load appointments');
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('button[aria-label], .alert button') as HTMLButtonElement).click();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Unable to load appointments');
    expect(retries).toBe(1);
  });

  it('renders the existing generic empty state', () => {
    fixture.componentRef.setInput('appointments', []);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('table')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('No appointments');
    expect(fixture.nativeElement.textContent).toContain('No appointment is available.');
  });

  it('passes pagination inputs through unchanged', () => {
    fixture.componentRef.setInput('page', 2);
    fixture.componentRef.setInput('totalPages', 6);
    fixture.componentRef.setInput('totalElements', 58);
    fixture.componentRef.setInput('size', 10);
    fixture.detectChanges();

    const pagination = fixture.debugElement.query(By.directive(Pagination)).componentInstance as Pagination;
    expect(pagination.page()).toBe(2);
    expect(pagination.totalPages()).toBe(6);
    expect(pagination.totalElements()).toBe(58);
    expect(pagination.size()).toBe(10);
  });

  it('keeps ordinary row content non-interactive', () => {
    const edited: Appointment[] = [];
    component.editRequested.subscribe((appointment) => edited.push(appointment));
    const row = fixture.nativeElement.querySelector('tbody tr') as HTMLTableRowElement;
    const dateCell = row.querySelector('td:nth-child(3)') as HTMLTableCellElement;

    row.click();
    dateCell.click();
    row.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    row.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true }));

    expect(row.classList.contains('cursor-pointer')).toBe(false);
    expect(row.hasAttribute('role')).toBe(false);
    expect(row.hasAttribute('tabindex')).toBe(false);
    expect(edited).toEqual([]);
  });

  it('links the patient name to the correct patient without exposing the row as a button', () => {
    let edited: Appointment | undefined;
    component.editRequested.subscribe((appointment) => (edited = appointment));
    const row = fixture.nativeElement.querySelector('tbody tr') as HTMLTableRowElement;
    const patientLink = row.querySelector('td:first-child a.table-record-link') as HTMLAnchorElement;
    const navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    patientLink.click();

    expect(row.hasAttribute('role')).toBe(false);
    expect(row.hasAttribute('tabindex')).toBe(false);
    expect(patientLink.getAttribute('href')).toBe('/patients/7');
    expect(navigateByUrl).toHaveBeenCalledOnce();
    expect(edited).toBeUndefined();
  });

  it('hides the doctor column entirely when the parent denies access (e.g. a MEDECIN, who only ever sees their own appointments)', () => {
    const headerCells = Array.from(fixture.nativeElement.querySelectorAll('thead th')) as HTMLTableCellElement[];
    const row = fixture.nativeElement.querySelector('tbody tr') as HTMLTableRowElement;

    expect(headerCells.some((th) => th.textContent?.includes('Doctor'))).toBe(false);
    expect(row.textContent).not.toContain('Bernard Sophie');
  });

  it('links the doctor to the correct route when the parent grants access', () => {
    fixture.componentRef.setInput('canViewMedecin', true);
    fixture.detectChanges();

    const doctorLink = fixture.nativeElement.querySelector('tbody td:nth-child(2) a.table-record-link') as HTMLAnchorElement;
    const navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    doctorLink.click();

    expect(doctorLink.getAttribute('href')).toBe('/medecins/4');
    expect(navigateByUrl).toHaveBeenCalledOnce();
  });

  it('emits edit only from the explicit edit button', () => {
    const edited: Appointment[] = [];
    component.editRequested.subscribe((appointment) => edited.push(appointment));

    (fixture.nativeElement.querySelector('button[aria-label="Edit"]') as HTMLButtonElement).click();

    expect(edited).toEqual([plannedAppointment]);
  });

  it('emits View only from the explicit eye button', () => {
    const viewed: Appointment[] = [];
    const edited: Appointment[] = [];
    component.viewRequested.subscribe((appointment) => viewed.push(appointment));
    component.editRequested.subscribe((appointment) => edited.push(appointment));

    (fixture.nativeElement.querySelector('button[aria-label="View"]') as HTMLButtonElement).click();

    expect(viewed).toEqual([plannedAppointment]);
    expect(edited).toEqual([]);
  });

  it('uses one eye for appointment View and a distinct medical icon for consultation View', () => {
    fixture.componentRef.setInput('appointments', [honoredAppointment]);
    fixture.detectChanges();

    const actions = fixture.nativeElement.querySelector('tbody td:last-child') as HTMLTableCellElement;
    const consultationButton = actions.querySelector('button[aria-label="View consultation"]') as HTMLButtonElement;

    expect(actions.querySelectorAll('i[type="eye"]')).toHaveLength(1);
    expect(consultationButton.querySelector('i[type="medicine-box"]')).not.toBeNull();
    expect(consultationButton.querySelector('i[type="eye"]')).toBeNull();
  });

  it('hides consultation View after lookup confirms that no consultation exists', () => {
    fixture.componentRef.setInput('appointments', [honoredAppointment]);
    fixture.componentRef.setInput('consultationUnavailableIds', new Set([honoredAppointment.id]));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('button[aria-label="View consultation"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('button[aria-label="View"]')).not.toBeNull();
  });

  it('emits delete without emitting edit', () => {
    let deleted: Appointment | undefined;
    let edited: Appointment | undefined;
    component.deleteRequested.subscribe((appointment) => (deleted = appointment));
    component.editRequested.subscribe((appointment) => (edited = appointment));

    (fixture.nativeElement.querySelector('button[aria-label="Delete"]') as HTMLButtonElement).click();

    expect(deleted).toBe(plannedAppointment);
    expect(edited).toBeUndefined();
  });

  it('emits a typed status-change request without emitting edit', () => {
    let emitted: { appointment: Appointment; statut: string } | undefined;
    let edited: Appointment | undefined;
    component.statusChangeRequested.subscribe((change) => (emitted = change));
    component.editRequested.subscribe((appointment) => (edited = appointment));

    (fixture.nativeElement.querySelector('button[aria-label="Confirm"]') as HTMLButtonElement).click();

    expect(emitted).toEqual({ appointment: plannedAppointment, statut: 'CONFIRME' });
    expect(edited).toBeUndefined();
  });

  it('disables status actions while that appointment is updating', () => {
    fixture.componentRef.setInput('statusUpdatingId', plannedAppointment.id);
    fixture.detectChanges();

    expect((fixture.nativeElement.querySelector('button[aria-label="Confirm"]') as HTMLButtonElement).disabled).toBe(true);
    expect((fixture.nativeElement.querySelector('button[aria-label="Mark completed"]') as HTMLButtonElement).disabled).toBe(true);
    expect((fixture.nativeElement.querySelector('button[aria-label="Cancel appointment"]') as HTMLButtonElement).disabled).toBe(true);
  });

  it('shows only valid status actions for confirmed appointments', () => {
    fixture.componentRef.setInput('appointments', [confirmedAppointment]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('button[aria-label="Confirm"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('button[aria-label="Mark completed"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('button[aria-label="Cancel appointment"]')).not.toBeNull();
  });

  it.each([honoredAppointment, cancelledAppointment])('hides status actions for terminal appointment $id', (appointment) => {
    fixture.componentRef.setInput('appointments', [appointment]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('button[aria-label="Confirm"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('button[aria-label="Mark completed"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('button[aria-label="Cancel appointment"]')).toBeNull();
  });

  it('shows and emits start consultation only when the parent rule allows it', () => {
    let emitted: Appointment | undefined;
    let edited: Appointment | undefined;
    component.startConsultationRequested.subscribe((appointment) => (emitted = appointment));
    component.editRequested.subscribe((appointment) => (edited = appointment));
    expect(fixture.nativeElement.querySelector('button[aria-label="Start consultation"]')).toBeNull();

    fixture.componentRef.setInput('canStartConsultation', () => true);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button[aria-label="Start consultation"]') as HTMLButtonElement).click();

    expect(emitted).toBe(plannedAppointment);
    expect(edited).toBeUndefined();
  });

  it('shows the consultation action for completed appointments and forwards it', () => {
    let emitted: Appointment | undefined;
    let edited: Appointment | undefined;
    component.viewConsultationRequested.subscribe((appointment) => (emitted = appointment));
    component.editRequested.subscribe((appointment) => (edited = appointment));
    fixture.componentRef.setInput('appointments', [honoredAppointment]);
    fixture.componentRef.setInput('consultationLookupId', honoredAppointment.id);
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('button[aria-label="View consultation"]') as HTMLButtonElement;
    expect(button.disabled).toBe(true);

    fixture.componentRef.setInput('consultationLookupId', null);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button[aria-label="View consultation"]') as HTMLButtonElement).click();

    expect(emitted).toBe(honoredAppointment);
    expect(edited).toBeUndefined();
    expect(fixture.nativeElement.querySelector('button[aria-label="Mark completed"]')).toBeNull();
  });

  it('forwards page changes from pagination', () => {
    let emitted: number | undefined;
    component.pageChange.subscribe((page) => (emitted = page));
    const pagination = fixture.debugElement.query(By.directive(Pagination)).componentInstance as Pagination;

    pagination.pageChange.emit(3);

    expect(emitted).toBe(3);
  });

  it('forwards page-size changes from pagination', () => {
    let emitted: number | undefined;
    component.sizeChange.subscribe((size) => (emitted = size));
    const pagination = fixture.debugElement.query(By.directive(Pagination)).componentInstance as Pagination;

    pagination.sizeChange.emit(25);

    expect(emitted).toBe(25);
  });
});
