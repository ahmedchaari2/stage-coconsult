import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { beforeEach, describe, expect, it } from 'vitest';

import { User } from 'src/app/core/models/user.model';
import { AuthService } from 'src/app/core/services/auth.service';
import { ProfileAvatarComponent } from '../avatar/profile-avatar.component';
import { ProfileViewComponent } from './profile-view.component';

@Component({ selector: 'app-profile-avatar', standalone: true, template: '<span>Avatar</span>' })
class ProfileAvatarStubComponent {}

describe('ProfileViewComponent', () => {
  let fixture: ComponentFixture<ProfileViewComponent>;
  const currentUser = signal<User | null>(null);

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ProfileViewComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            en: {
              nav: { breadcrumbHome: 'Home' },
              profile: {
                view: {
                  title: 'My Profile',
                  labels: { role: 'Role', createdAt: 'Account created on', status: 'Account status' },
                  sections: {
                    identity: 'Identity',
                    contact: 'Contact details',
                    professional: 'Professional information',
                    account: 'Account'
                  },
                  roles: { admin: 'Administrator', medecin: 'Doctor' },
                  incompleteProfessionalProfile: 'Complete professional information',
                  editButton: 'Edit profile',
                  changePasswordButton: 'Change password'
                },
                edit: {
                  labels: {
                    prenom: 'First name',
                    nom: 'Last name',
                    email: 'Email',
                    telephone: 'Professional phone',
                    specialite: 'Specialty',
                    numeroOrdre: 'License number'
                  }
                }
              },
              medecin: { gestion: { status: { actif: 'Active', inactif: 'Inactive' } } }
            }
          },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' }
        })
      ],
      providers: [provideRouter([]), { provide: AuthService, useValue: { currentUser } }]
    })
      .overrideComponent(ProfileViewComponent, {
        remove: { imports: [ProfileAvatarComponent] },
        add: { imports: [ProfileAvatarStubComponent] }
      })
      .compileComponents();
  });

  it('shows shared contact and account information without doctor fields for an admin', () => {
    currentUser.set({
      id: 1,
      email: 'admin@medtrack.tn',
      nom: 'Admin',
      prenom: 'Principal',
      role: 'ADMIN',
      telephone: '+216 70 000 000',
      createdAt: '2026-07-28T12:00:00Z'
    });

    fixture = TestBed.createComponent(ProfileViewComponent);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Administrator');
    expect(text).toContain('28/07/2026');
    expect(fixture.nativeElement.querySelector('a[href="tel:+216 70 000 000"]')).not.toBeNull();
    expect(text).not.toContain('Professional information');
  });

  it('shows professional credentials and status for a doctor', () => {
    currentUser.set({
      id: 12,
      email: 'doctor@medtrack.tn',
      nom: 'Chatti',
      prenom: 'Nabil',
      role: 'MEDECIN',
      actif: true,
      specialite: 'Cardiologie',
      numeroOrdre: 'OM-2020-0142'
    });

    fixture = TestBed.createComponent(ProfileViewComponent);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Professional information');
    expect(text).toContain('Cardiologie');
    expect(text).toContain('OM-2020-0142');
    expect(text).toContain('Active');
  });
});
