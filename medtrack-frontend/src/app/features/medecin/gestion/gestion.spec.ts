import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Medecin } from 'src/app/core/models/medecin.model';
import { InvitationSummary } from 'src/app/core/models/invitation.model';
import { InvitationService } from 'src/app/core/services/invitation.service';
import { MedecinService } from 'src/app/core/services/medecin.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { Gestion } from './gestion';

const medecin: Medecin = {
  id: 12,
  email: 'sophie.bernard@example.test',
  nom: 'Bernard',
  prenom: 'Sophie',
  role: 'MEDECIN',
  actif: true,
  specialite: 'Cardiologie',
  numeroOrdre: 'ORD-12'
};

const invitation: InvitationSummary = {
  id: 23,
  email: 'invited.doctor@example.test',
  role: 'MEDECIN',
  status: 'PENDING',
  expirationDate: '2026-07-31T12:00:00Z',
  createdAt: '2026-07-28T12:00:00Z',
  invitedByName: 'Admin MedTrack'
};

describe('Gestion navigation', () => {
  let fixture: ComponentFixture<Gestion>;
  let component: Gestion;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        Gestion,
        TranslocoTestingModule.forRoot({
          langs: {
            en: {
              medecin: {
                gestion: {
                  listTitle: 'Doctors',
                  tabs: { label: 'Doctor management sections' },
                  columns: {
                    medecin: 'Doctor',
                    email: 'Email',
                    specialite: 'Specialty',
                    numeroOrdre: 'Order',
                    statut: 'Status'
                  }
                },
                actions: { deactivate: 'Deactivate', reactivate: 'Reactivate' },
                statut: { actif: 'Active', inactif: 'Inactive' }
              },
              common: {
                loading: 'Loading',
                labels: { actions: 'Actions' },
                buttons: { view: 'View', edit: 'Edit', previous: 'Previous', next: 'Next' }
              },
              pagination: { show: 'Show', perPage: 'per page', noItems: 'No items', of: 'of', page: 'Page', item: 'item', items: 'items' },
              invitation: {
                form: { title: 'Invite a doctor' },
                list: {
                  title: 'Invitations',
                  loading: 'Loading invitations',
                  columns: { email: 'Email', statut: 'Status', expiration: 'Expires on', invitedBy: 'Invited by' }
                },
                status: { pending: 'Pending' }
              }
            }
          },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' }
        })
      ],
      providers: [
        provideRouter([]),
        {
          provide: MedecinService,
          useValue: { getMedecins: () => of({ content: [medecin], page: 0, size: 10, totalPages: 1, totalElements: 1 }) }
        },
        { provide: InvitationService, useValue: { getInvitations: () => of([invitation]) } },
        { provide: NotificationService, useValue: {} }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Gestion);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('uses the correct doctor ID for both detail links', () => {
    const row = fixture.nativeElement.querySelector('tbody tr') as HTMLTableRowElement;
    const links = row.querySelectorAll('a[href="/medecins/12"]');

    expect(links.length).toBe(2);
    expect((links[0] as HTMLAnchorElement).classList.contains('table-record-link')).toBe(true);
  });

  it('renders the complete doctor name in one linked column', () => {
    const headers = fixture.nativeElement.querySelectorAll('#medecins-panel thead th') as NodeListOf<HTMLTableCellElement>;
    const doctorLink = fixture.nativeElement.querySelector('#medecins-panel tbody td:first-child a.table-record-link') as HTMLAnchorElement;

    expect(headers).toHaveLength(6);
    expect(headers[0].textContent).toContain('Doctor');
    expect(doctorLink.textContent).toContain('Bernard Sophie');
    expect(doctorLink.getAttribute('href')).toBe('/medecins/12');
  });

  it('separates doctors and invitations into accessible tabs', () => {
    const tabs = fixture.nativeElement.querySelectorAll('[role="tab"]') as NodeListOf<HTMLButtonElement>;

    expect(tabs.length).toBe(2);
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');
    expect(fixture.nativeElement.querySelector('#medecins-panel')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#invitations-panel')).toBeNull();
    expect(tabs[1].textContent).toContain('1');

    tabs[1].click();
    fixture.detectChanges();

    expect(tabs[1].getAttribute('aria-selected')).toBe('true');
    expect(fixture.nativeElement.querySelector('#medecins-panel')).toBeNull();
    expect(fixture.nativeElement.querySelector('#invitations-panel')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('invited.doctor@example.test');
  });

  it('keeps edit and destructive actions separate from detail navigation', () => {
    const navigate = vi.spyOn(router, 'navigate');
    const navigateByUrl = vi.spyOn(router, 'navigateByUrl');
    const edit = vi.spyOn(component, 'onEdit').mockImplementation(() => undefined);
    const toggle = vi.spyOn(component, 'onToggle').mockImplementation(() => undefined);
    const row = fixture.nativeElement.querySelector('tbody tr') as HTMLTableRowElement;

    (row.querySelector('button[aria-label="Edit"]') as HTMLButtonElement).click();
    (row.querySelector('button[aria-label="Deactivate"]') as HTMLButtonElement).click();

    expect(edit).toHaveBeenCalledWith(medecin);
    expect(toggle).toHaveBeenCalledWith(medecin);
    expect(navigate).not.toHaveBeenCalled();
    expect(navigateByUrl).not.toHaveBeenCalled();
  });
});
