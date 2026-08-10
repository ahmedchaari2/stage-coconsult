import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { MedecinService } from 'src/app/core/services/medecin.service';
import { MedecinDetail } from './medecin-detail';

describe('MedecinDetail', () => {
  let fixture: ComponentFixture<MedecinDetail>;

  const medecinService = {
    getMedecin: vi.fn(() =>
      of({
        id: 12,
        email: 'nabil.chatti@medtrack.tn',
        nom: 'Chatti',
        prenom: 'Nabil',
        role: 'MEDECIN' as const,
        actif: true,
        specialite: 'Cardiologie',
        numeroOrdre: 'OM-2020-0142',
        telephone: '+216 20 123 456',
        createdAt: '2026-07-28T12:00:00Z'
      })
    )
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        MedecinDetail,
        TranslocoTestingModule.forRoot({
          langs: {
            en: {
              nav: { breadcrumbHome: 'Home', medecins: { list: 'Doctors' } },
              medecin: {
                detail: {
                  title: 'Doctor details',
                  sectionTitle: 'Doctor information',
                  identitySection: 'Identity',
                  professionalSection: 'Professional information',
                  contactSection: 'Contact details',
                  accountSection: 'Account',
                  createdAt: 'Account created on',
                  empty: { text: 'Not found' }
                },
                edit: {
                  title: 'Edit doctor',
                  loading: 'Loading',
                  labels: {
                    nom: 'Last name',
                    prenom: 'First name',
                    email: 'Email',
                    specialite: 'Specialty',
                    numeroOrdre: 'License number',
                    telephone: 'Professional phone'
                  },
                  placeholders: {},
                  errors: {}
                },
                gestion: { columns: { statut: 'Status' }, empty: { title: 'No doctor' }, status: { actif: 'Active', inactif: 'Inactive' } }
              },
              common: { buttons: { edit: 'Edit', backToList: 'Back' }, modal: { confirmDiscard: 'Discard?' } }
            }
          },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' }
        })
      ],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '12' })) } },
        { provide: MedecinService, useValue: medecinService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MedecinDetail);
    fixture.detectChanges();
  });

  it('renders professional, contact, and account information', () => {
    const text = fixture.nativeElement.textContent;

    expect(text).toContain('Cardiologie');
    expect(text).toContain('OM-2020-0142');
    expect(fixture.nativeElement.querySelector('a[href="tel:+216 20 123 456"]')).not.toBeNull();
    expect(text).toContain('28/07/2026');
  });
});
