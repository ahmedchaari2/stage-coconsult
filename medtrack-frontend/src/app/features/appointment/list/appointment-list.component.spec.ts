import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { IconService } from '@ant-design/icons-angular';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Appointment } from 'src/app/core/models/appointment.model';
import { AppointmentService } from 'src/app/core/services/appointment.service';
import { AuthService } from 'src/app/core/services/auth.service';
import { MedicalRecordService } from 'src/app/core/services/medical-record.service';
import { MedecinService } from 'src/app/core/services/medecin.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { PatientService } from 'src/app/core/services/patient.service';
import { AppointmentListComponent } from './appointment-list.component';

describe('AppointmentListComponent consultation navigation', () => {
  const navigate = vi.fn();
  const appointment = {
    id: 31,
    patientId: 7,
    statut: 'HONORE'
  } as Appointment;

  beforeEach(() => {
    navigate.mockReset();
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AppointmentService,
          useValue: {
            getAppointmentById: () => of(appointment),
            getAppointments: () => of({ content: [], totalPages: 0, totalElements: 0, size: 10, number: 0 })
          }
        },
        { provide: PatientService, useValue: {} },
        { provide: MedecinService, useValue: {} },
        {
          provide: MedicalRecordService,
          useValue: { getConsultationByAppointment: () => of({ exists: true, consultationId: 77 }) }
        },
        { provide: AuthService, useValue: { currentUser: signal({ id: 4, role: 'MEDECIN' }) } },
        { provide: NotificationService, useValue: { showWarning: vi.fn(), showError: vi.fn() } },
        { provide: TranslocoService, useValue: { translate: (key: string) => key } },
        { provide: NgbModal, useValue: { open: vi.fn() } },
        { provide: IconService, useValue: { addIcon: vi.fn() } },
        { provide: ActivatedRoute, useValue: {} },
        { provide: Router, useValue: { navigate } }
      ]
    });
  });

  it('deep-links the resolved consultation into the correct patient dossier', () => {
    const component = TestBed.runInInjectionContext(() => new AppointmentListComponent());

    component.onViewConsultation(appointment);

    expect(navigate).toHaveBeenCalledWith(['/patients', 7], {
      queryParams: { consultationId: 77 }
    });
  });

  it('marks consultation View unavailable when the lookup has no destination', () => {
    vi.spyOn(TestBed.inject(MedicalRecordService), 'getConsultationByAppointment').mockReturnValue(
      of({ exists: false, consultationId: null })
    );
    const component = TestBed.runInInjectionContext(() => new AppointmentListComponent());

    component.onViewConsultation(appointment);

    expect(component.consultationUnavailableIds().has(appointment.id)).toBe(true);
    expect(component.consultationLookupId()).toBeNull();
    expect(TestBed.inject(NotificationService).showWarning).toHaveBeenCalledOnce();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('loads the selected appointment by ID for the read-only View modal', () => {
    const service = TestBed.inject(AppointmentService);
    const getAppointmentById = vi.spyOn(service, 'getAppointmentById');
    const component = TestBed.runInInjectionContext(() => new AppointmentListComponent());

    component.openViewModal(appointment);

    expect(getAppointmentById).toHaveBeenCalledWith(31);
    expect(component.viewedAppointment()).toBe(appointment);
    expect(component.appointmentViewLoading()).toBe(false);
  });

  it('opens a busy calendar day in the existing list filtered to that date', () => {
    const component = TestBed.runInInjectionContext(() => new AppointmentListComponent());
    component.searchInput.set('patient');
    component.searchTerm.set('patient');
    component.statutFilter.set('HONORE');
    component.medecinIdFilter.set(9);
    component.viewMode.set('calendar');

    component.onCalendarDayAppointmentsClick('2026-07-15');

    expect(component.viewMode()).toBe('list');
    expect(component.searchTerm()).toBe('');
    expect(component.statutFilter()).toBe('');
    expect(component.medecinIdFilter()).toBeNull();
    expect(component.dateFromFilter()).toBe('2026-07-15');
    expect(component.dateToFilter()).toBe('2026-07-15');
    expect(component.filtersPanelOpen()).toBe(true);
  });
});
