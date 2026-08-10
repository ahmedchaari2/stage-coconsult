import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { IconService } from '@ant-design/icons-angular';
import { TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';
import { describe, expect, it, beforeEach, vi } from 'vitest';

import { AdminDashboard, AujourdhuiResponse, MedecinDashboard } from 'src/app/core/models/dashboard.model';
import { DashboardService } from 'src/app/core/services/dashboard.service';
import { AdminDashboardComponent } from './admin/admin-dashboard.component';
import { MedecinDashboardComponent } from './medecin/medecin-dashboard.component';

describe('dashboard list pagination', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: DashboardService,
          useValue: { getAdminDashboard: () => of({}), getAujourdhui: () => of({}), getMedecinDashboard: () => of({}) }
        },
        { provide: TranslocoService, useValue: { translate: (key: string) => key } },
        { provide: IconService, useValue: { addIcon: vi.fn() } },
        { provide: Router, useValue: { navigate: vi.fn() } }
      ]
    });
  });

  it('pages admin appointments and doctor activity independently', () => {
    const component = TestBed.runInInjectionContext(() => new AdminDashboardComponent());
    component.aujourdhui.set({
      disponible: true,
      rendezVous: Array.from({ length: 12 }, (_, index) => ({
        id: index + 1,
        dateHeure: '2026-07-28T09:00:00',
        statut: 'PLANIFIE',
        patientId: 100 + index,
        patientNom: 'Patient',
        patientPrenom: String(index + 1),
        medecinId: 2,
        medecinNom: 'Doctor',
        medecinPrenom: 'One',
        motif: 'Check-up'
      })),
      aConfirmer: 0,
      annulesAujourdhui: 0,
      prochainesPlagesLibres: []
    } as AujourdhuiResponse);
    component.data.set({
      activiteParMedecin: {
        disponible: true,
        items: Array.from({ length: 11 }, (_, index) => ({ medecinId: index + 1 }))
      }
    } as AdminDashboard);

    expect(component.aujourdhuiItems().map((item) => item.id)).toEqual([1, 2, 3, 4, 5]);
    expect(component.aujourdhuiTotalPages()).toBe(3);
    component.onAujourdhuiPageChange(2);
    expect(component.aujourdhuiItems().map((item) => item.id)).toEqual([11, 12]);

    expect(component.activiteItems().map((item) => item.medecinId)).toEqual([1, 2, 3, 4, 5]);
    component.onActivitePageChange(1);
    expect(component.activiteItems().map((item) => item.medecinId)).toEqual([6, 7, 8, 9, 10]);
  });

  it('resets dashboard list pages when page size changes', () => {
    const component = TestBed.runInInjectionContext(() => new AdminDashboardComponent());
    component.aujourdhuiPage.set(2);
    component.activitePage.set(2);

    component.onAujourdhuiSizeChange(10);
    component.onActiviteSizeChange(10);

    expect(component.aujourdhuiPage()).toBe(0);
    expect(component.activitePage()).toBe(0);
  });

  it('keeps free slots compact until the user expands them', () => {
    const component = TestBed.runInInjectionContext(() => new AdminDashboardComponent());
    component.aujourdhui.set({
      disponible: true,
      rendezVous: [],
      aConfirmer: 0,
      annulesAujourdhui: 0,
      prochainesPlagesLibres: Array.from({ length: 7 }, (_, index) => ({
        medecinId: index + 1,
        medecinNom: `Doctor ${index + 1}`,
        medecinPrenom: 'Test',
        heure: '11:30:00'
      }))
    } as AujourdhuiResponse);

    expect(component.freeSlotItems()).toHaveLength(4);
    expect(component.hiddenFreeSlotCount()).toBe(3);

    component.toggleFreeSlots();

    expect(component.freeSlotItems()).toHaveLength(7);
    expect(component.hiddenFreeSlotCount()).toBe(0);
  });

  it('keeps treatments as a five-item preview and pages patients without records', () => {
    const component = TestBed.runInInjectionContext(() => new MedecinDashboardComponent());
    component.data.set({
      traitementsAEcheance: {
        disponible: true,
        total: 8,
        items: Array.from({ length: 8 }, (_, index) => ({ prescriptionId: index + 1 }))
      },
      patientsSansDossier: {
        disponible: true,
        total: 7,
        items: Array.from({ length: 7 }, (_, index) => ({ patientId: index + 1 }))
      }
    } as MedecinDashboard);

    expect(component.traitementsPreview().map((item) => item.prescriptionId)).toEqual([1, 2, 3, 4, 5]);
    expect(component.patientsSansDossierTotalPages()).toBe(2);
    component.onPatientsSansDossierPageChange(1);
    expect(component.patientsSansDossierItems().map((item) => item.patientId)).toEqual([6, 7]);
  });
});
