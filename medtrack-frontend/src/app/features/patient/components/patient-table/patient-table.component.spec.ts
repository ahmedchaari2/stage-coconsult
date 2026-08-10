import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Patient } from 'src/app/core/models/patient.model';
import { Pagination } from 'src/app/theme/shared/components/pagination/pagination';
import { PatientTableComponent } from './patient-table.component';

const activePatient: Patient = {
  id: 17,
  nom: 'Martin',
  prenom: 'Alice',
  dateNaissance: '1990-05-12',
  telephone: '+216 20 123 456',
  email: 'alice@example.test',
  adresse: '1 rue Test',
  medecinReferentId: 4,
  numeroDossier: 'PAT-2026-0017',
  archived: false
};

const archivedPatient: Patient = {
  ...activePatient,
  id: 18,
  nom: 'Durand',
  prenom: 'Paul',
  email: 'paul@example.test',
  archived: true
};

describe('PatientTableComponent', () => {
  let fixture: ComponentFixture<PatientTableComponent>;
  let component: PatientTableComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        PatientTableComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            en: {
              patient: {
                list: {
                  loading: 'Loading patients',
                  columns: {
                    patient: 'Patient',
                    dateNaissance: 'Birth date',
                    telephone: 'Phone',
                    email: 'Email',
                    medecinReferent: 'Referring doctor',
                    numeroDossier: 'File number'
                  },
                  viewRow: 'View {{ nom }} {{ prenom }}',
                  empty: { title: 'No patients', text: 'No patient is available.' }
                }
              },
              common: {
                labels: { actions: 'Actions', archived: 'Archived' },
                buttons: {
                  view: 'View',
                  edit: 'Edit',
                  archive: 'Archive',
                  restore: 'Restore',
                  retry: 'Retry',
                  previous: 'Previous',
                  next: 'Next'
                },
                search: { noResults: 'No results for {{ term }}', reset: 'Reset search' }
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

    fixture = TestBed.createComponent(PatientTableComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('patients', [activePatient]);
    fixture.componentRef.setInput('medecinReferentLabel', (patient: Patient) => `Doctor ${patient.medecinReferentId}`);
    fixture.componentRef.setInput('isAdmin', true);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('renders patient rows and contact details', () => {
    const row = fixture.nativeElement.querySelector('tbody tr') as HTMLTableRowElement;

    expect(row).not.toBeNull();
    expect(row.textContent).toContain('Martin');
    expect(row.textContent).toContain('Alice');
    expect(row.textContent).toContain('Doctor 4');
    expect(row.textContent).toContain('PAT-2026-0017');
    expect(row.querySelector('a[href^="tel:"]')?.getAttribute('href')).toBe('tel:+216 20 123 456');
    expect(row.querySelector('a[href="mailto:alice@example.test"]')).not.toBeNull();
  });

  it('renders the complete patient name in one linked column', () => {
    const headers = fixture.nativeElement.querySelectorAll('thead th') as NodeListOf<HTMLTableCellElement>;
    const patientLink = fixture.nativeElement.querySelector('tbody td:first-child a.table-record-link') as HTMLAnchorElement;

    expect(headers).toHaveLength(7);
    expect(headers[0].textContent).toContain('Patient');
    expect(patientLink.textContent).toContain('Martin Alice');
    expect(patientLink.getAttribute('href')).toBe('/patients/17');
  });

  it('hides the doctor referent column entirely for a MEDECIN, who only ever sees their own referent patients', () => {
    fixture.componentRef.setInput('isAdmin', false);
    fixture.detectChanges();

    const headers = fixture.nativeElement.querySelectorAll('thead th') as NodeListOf<HTMLTableCellElement>;
    const row = fixture.nativeElement.querySelector('tbody tr') as HTMLTableRowElement;

    expect(headers).toHaveLength(6);
    expect(row.textContent).not.toContain('Doctor 4');
  });

  it('renders the empty state for an empty collection', () => {
    fixture.componentRef.setInput('patients', []);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('table')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('No patients');
    expect(fixture.nativeElement.textContent).toContain('No patient is available.');
  });

  it('renders the loading state without the table', () => {
    fixture.componentRef.setInput('isLoading', true);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-spinner')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Loading patients');
  });

  it('renders the error state and emits retry', () => {
    let retries = 0;
    component.retryRequested.subscribe(() => retries++);
    fixture.componentRef.setInput('error', 'Unable to load patients');
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[role="alert"] button') as HTMLButtonElement).click();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Unable to load patients');
    expect(retries).toBe(1);
  });

  it('passes pagination inputs through unchanged', () => {
    fixture.componentRef.setInput('page', 2);
    fixture.componentRef.setInput('totalPages', 5);
    fixture.componentRef.setInput('totalElements', 47);
    fixture.componentRef.setInput('size', 10);
    fixture.detectChanges();

    const pagination = fixture.debugElement.query(By.directive(Pagination)).componentInstance as Pagination;
    expect(pagination.page()).toBe(2);
    expect(pagination.totalPages()).toBe(5);
    expect(pagination.totalElements()).toBe(47);
    expect(pagination.size()).toBe(10);
  });

  it('uses a native eye link for the patient View destination', () => {
    const viewLink = fixture.nativeElement.querySelector('a[aria-label="View"]') as HTMLAnchorElement;

    expect(viewLink.getAttribute('href')).toBe('/patients/17');
    expect(viewLink.querySelector('i[type="eye"]')).not.toBeNull();
  });

  it('does not expose or activate the complete row as a View control', () => {
    const navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const row = fixture.nativeElement.querySelector('tbody tr') as HTMLTableRowElement;

    row.click();
    row.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    row.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true }));

    expect(row.classList.contains('cursor-pointer')).toBe(false);
    expect(row.hasAttribute('role')).toBe(false);
    expect(row.hasAttribute('tabindex')).toBe(false);
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('emits edit without navigating to View', () => {
    let edited: Patient | undefined;
    const navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    component.editRequested.subscribe((patient) => (edited = patient));

    (fixture.nativeElement.querySelector('button[aria-label="Edit"]') as HTMLButtonElement).click();

    expect(edited).toBe(activePatient);
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('emits archive for an active patient', () => {
    let emitted: Patient | undefined;
    component.archiveToggleRequested.subscribe((patient) => (emitted = patient));

    (fixture.nativeElement.querySelector('button[aria-label="Archive"]') as HTMLButtonElement).click();

    expect(emitted).toBe(activePatient);
  });

  it('emits restore for an archived patient when restoration is allowed', () => {
    let emitted: Patient | undefined;
    component.archiveToggleRequested.subscribe((patient) => (emitted = patient));
    fixture.componentRef.setInput('patients', [archivedPatient]);
    fixture.componentRef.setInput('canRestoreArchived', true);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('button[aria-label="Restore"]') as HTMLButtonElement).click();

    expect(emitted).toBe(archivedPatient);
    expect(fixture.nativeElement.querySelector('button[aria-label="Edit"]')).toBeNull();
  });

  it('hides restore for archived patients when restoration is not allowed', () => {
    fixture.componentRef.setInput('patients', [archivedPatient]);
    fixture.componentRef.setInput('canRestoreArchived', false);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('button[aria-label="Restore"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('button[aria-label="Edit"]')).toBeNull();
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

  it('uses a native patient-name link without exposing the row as a button', () => {
    const row = fixture.nativeElement.querySelector('tbody tr') as HTMLTableRowElement;
    const patientLink = row.querySelector('td:first-child a.table-record-link') as HTMLAnchorElement;

    expect(row.hasAttribute('role')).toBe(false);
    expect(row.hasAttribute('tabindex')).toBe(false);
    expect(patientLink.getAttribute('href')).toBe('/patients/17');
  });
});
