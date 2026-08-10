import { Component, inject, OnInit, signal, computed, TemplateRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute, RouterModule } from '@angular/router';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import {
  EditOutline,
  MedicineBoxOutline,
  InboxOutline,
  RollbackOutline,
  HistoryOutline,
  InfoCircleOutline,
  AuditOutline,
  WarningOutline
} from '@ant-design/icons-angular/icons';
import { CardComponent } from 'src/app/theme/shared/components/card/card.component';
import { SpinnerComponent } from 'src/app/theme/shared/components/spinner/spinner.component';
import { Spinkit } from 'src/app/theme/shared/components/spinner/spinkits';
import { PatientService } from 'src/app/core/services/patient.service';
import { MedecinService } from 'src/app/core/services/medecin.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { AuthService } from 'src/app/core/services/auth.service';
import { Patient } from 'src/app/core/models/patient.model';
import { MedecinOption } from 'src/app/core/models/medecin.model';
import { MedicalRecord as MedicalRecordDto } from 'src/app/core/models/medical-record.model';
import { MedicalRecord, ConsultationPrefill } from 'src/app/features/patient/medical-record/medical-record';
import { PatientHistoryComponent } from 'src/app/features/patient/history/patient-history.component';
import { PatientEditModalComponent } from 'src/app/features/patient/edit-modal/patient-edit-modal';
import { PatientAiChatComponent } from 'src/app/features/patient/ai-chat/patient-ai-chat.component';
import { PhoneLinkComponent } from 'src/app/theme/shared/components/phone-link/phone-link';
import { EmailLinkComponent } from 'src/app/theme/shared/components/email-link/email-link';
import { DateFormatPipe } from 'src/app/core/pipes/date-format.pipe';

@Component({
  selector: 'app-patient-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    CardComponent,
    SpinnerComponent,
    NgbModalModule,
    TranslocoModule,
    IconDirective,
    MedicalRecord,
    PatientHistoryComponent,
    PatientEditModalComponent,
    PatientAiChatComponent,
    PhoneLinkComponent,
    EmailLinkComponent,
    DateFormatPipe
  ],
  templateUrl: './patient-detail.component.html',
  styleUrl: './patient-detail.component.scss'
})
export class PatientDetailComponent implements OnInit {
  private readonly patientService = inject(PatientService);
  private readonly medecinService = inject(MedecinService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly modalService = inject(NgbModal);
  private readonly notificationService = inject(NotificationService);
  private readonly authService = inject(AuthService);
  private readonly iconService = inject(IconService);
  private readonly translocoService = inject(TranslocoService);

  constructor() {
    this.iconService.addIcon(
      EditOutline,
      MedicineBoxOutline,
      InboxOutline,
      RollbackOutline,
      HistoryOutline,
      InfoCircleOutline,
      AuditOutline,
      WarningOutline
    );
  }

  spinkit = Spinkit.skLine;

  activeTab = signal<'information' | 'record' | 'history'>('information');

  consultationPrefill = signal<ConsultationPrefill | null>(null);

  targetConsultationId = signal<number | null>(null);
  private readonly failedConsultationTargets = new Set<string>();
  private activeConsultationTargetKey: string | null = null;

  patient = signal<Patient | null>(null);
  isLoading = signal(true);
  error = signal<string | null>(null);
  patientId = signal<number | null>(null);

  pendingPatient: Patient | null = null;
  pendingAction: 'archive' | 'restore' = 'archive';

  readonly isArchived = computed(() => this.patient()?.archived === true);

  medicalRecord = signal<MedicalRecordDto | null>(null);

  readonly age = computed(() => {
    const p = this.patient();
    if (!p?.dateNaissance) return null;
    const birth = new Date(p.dateNaissance);
    if (isNaN(birth.getTime())) return null;
    const now = new Date();
    let years = now.getFullYear() - birth.getFullYear();
    const m = now.getMonth() - birth.getMonth();
    if (m < 0 || (m === 0 && now.getDate() < birth.getDate())) years--;
    return years >= 0 ? years : null;
  });

  @ViewChild('confirmModal') confirmModal!: TemplateRef<unknown>;

  @ViewChild(PatientEditModalComponent) private editModalCmp!: PatientEditModalComponent;

  readonly isAdmin = computed(() => this.authService.currentUser()?.role === 'ADMIN');

  medecins = signal<MedecinOption[]>([]);

  medecinReferentDisplay = computed(() => {
    const p = this.patient();
    if (!p || p.medecinReferentId == null) return this.translocoService.translate('common.labels.unassigned');
    const med = this.medecins().find((m) => Number(m.id) === Number(p.medecinReferentId));
    return med ? `${med.nom} ${med.prenom}` : `#${p.medecinReferentId}`;
  });

  canEditMedicalRecord = computed(() => {
    const user = this.authService.currentUser();
    const p = this.patient();
    if (!user || !p) return false;
    if (p.archived) return false;
    if (user.role === 'ADMIN') return true;
    return user.role === 'MEDECIN' && p.medecinReferentId != null && Number(p.medecinReferentId) === Number(user.id);
  });

  ngOnInit(): void {
    this.loadMedecins();

    this.route.paramMap.subscribe((params) => {
      const idParam = params.get('id');
      if (idParam) {
        const id = +idParam;
        this.patientId.set(id);
        this.loadPatient(id);
      } else {
        this.error.set(this.translocoService.translate('patient.errors.idMissing'));
        this.isLoading.set(false);
      }
    });

    this.route.queryParamMap.subscribe((params) => {
      const section = params.get('section');
      this.activeTab.set(section === 'record' || section === 'history' ? section : 'information');

      const consultationIdParam = params.get('consultationId');
      if (consultationIdParam !== null) {
        const consultationId = Number(consultationIdParam);
        if (Number.isSafeInteger(consultationId) && consultationId > 0) {
          this.activeConsultationTargetKey = String(consultationId);
          this.targetConsultationId.set(consultationId);
          this.activeTab.set('record');
        } else {
          this.handleTargetConsultationFailure(consultationIdParam);
        }
      } else {
        this.activeConsultationTargetKey = null;
        this.targetConsultationId.set(null);
      }

      const appointmentIdParam = params.get('appointmentId');
      if (params.get('openConsultation') && appointmentIdParam) {
        this.activeTab.set('record');
        this.consultationPrefill.set({
          appointmentId: +appointmentIdParam,
          motif: params.get('motif') ?? '',
          date: params.get('date') ?? ''
        });
        this.router.navigate([], { relativeTo: this.route, queryParams: { section: 'record' }, replaceUrl: true });
      }
    });
  }

  onTargetConsultationFailed(): void {
    if (this.activeConsultationTargetKey !== null) {
      this.handleTargetConsultationFailure(this.activeConsultationTargetKey);
    }
  }

  private handleTargetConsultationFailure(targetKey: string): void {
    if (this.failedConsultationTargets.has(targetKey)) {
      return;
    }
    this.failedConsultationTargets.add(targetKey);
    this.activeConsultationTargetKey = null;
    this.targetConsultationId.set(null);
    this.notificationService.showWarning(this.translocoService.translate('medicalRecord.errors.requestedConsultationUnavailable'));
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { consultationId: null },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  /**
   * GET /medecins/all est réservé ADMIN (403 pour un MEDECIN, comme dans les autres listes —
   * voir patient-list/appointment-list/prescription-list). Un MEDECIN ne reçoit donc jamais la
   * liste complète : on préremplit au moins sa propre entrée pour que medecinDisplay() résolve
   * son nom sur ses propres consultations/rendez-vous au lieu d'afficher "Médecin #id".
   */
  private loadMedecins(): void {
    const user = this.authService.currentUser();
    if (user) {
      this.medecins.set([{ id: user.id, nom: user.nom, prenom: user.prenom }]);
    }
    if (!this.isAdmin()) return;

    this.medecinService.getAllMedecinOptions().subscribe({
      next: (options) => this.medecins.set(options),
      error: () => {
        /* silence : géré par le fallback medecinReferentDisplay */
      }
    });
  }

  loadPatient(id: number): void {
    this.isLoading.set(true);
    this.error.set(null);

    this.medicalRecord.set(null);

    this.patientService.getPatientById(id).subscribe({
      next: (data) => {
        this.patient.set(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.error.set(
          this.translocoService.translate(
            err.status === 404
              ? 'patient.errors.notFound'
              : err.status === 401
                ? 'common.errors.unauthorized'
                : err.status === 403
                  ? 'common.errors.adminRequired'
                  : 'patient.errors.loadOneFailed'
          )
        );
        this.isLoading.set(false);
      }
    });
  }

  onEdit(): void {
    const p = this.patient();
    if (!p) return;
    this.editModalCmp.open(p);
  }

  onPatientSaved(updated: Patient): void {
    this.patient.set(updated);
  }

  onArchiveToggle(): void {
    const patient = this.patient();
    const id = this.patientId();

    if (!patient || !id) return;

    this.pendingPatient = patient;
    this.pendingAction = patient.archived ? 'restore' : 'archive';
    this.modalService.open(this.confirmModal, { centered: true, backdrop: 'static' }).result.then(
      (result) => {
        if (result !== 'confirm') {
          return;
        }

        this.isLoading.set(true);

        const request$ = patient.archived ? this.patientService.restorePatient(id) : this.patientService.archivePatient(id);

        request$.subscribe({
          next: () => {
            this.loadPatient(id);
          },
          error: (err) => {
            const forbiddenKey = this.pendingAction === 'restore' ? 'patient.errors.restoreForbidden' : 'patient.errors.archiveForbidden';
            const failedKey = this.pendingAction === 'restore' ? 'patient.errors.restoreFailed' : 'patient.errors.archiveFailed';
            this.notificationService.showError(
              this.translocoService.translate(
                err.status === 403 ? forbiddenKey : err.status === 404 ? 'patient.errors.notFound' : failedKey
              )
            );
            this.isLoading.set(false);
          }
        });
      },
      () => {}
    );
  }

  setActiveTab(tab: 'information' | 'record' | 'history'): void {
    this.activeTab.set(tab);
    const queryParams: Record<string, string | null> = {
      section: tab === 'information' ? null : tab
    };
    if (tab !== 'record') {
      queryParams['consultationId'] = null;
    }
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge'
    });
  }

  onRecordLoaded(record: MedicalRecordDto | null): void {
    this.medicalRecord.set(record);
  }

  onBack(): void {
    this.router.navigate(['/patients']);
  }

  onViewAccesLog(): void {
    const p = this.patient();
    if (!p) return;
    this.router.navigate(['/journal-acces'], { queryParams: { patientId: p.id, patientLabel: `${p.nom} ${p.prenom}` } });
  }

  private readonly groupeSanguinLabels: Record<string, string> = {
    A_POSITIF: 'A+',
    A_NEGATIF: 'A-',
    B_POSITIF: 'B+',
    B_NEGATIF: 'B-',
    AB_POSITIF: 'AB+',
    AB_NEGATIF: 'AB-',
    O_POSITIF: 'O+',
    O_NEGATIF: 'O-'
  };

  groupeSanguinLabel(): string {
    const g = this.medicalRecord()?.groupeSanguin;
    return g ? (this.groupeSanguinLabels[g] ?? g) : '—';
  }

  get pageTitle(): string | null {
    const patient = this.patient();
    return patient ? `${patient.nom} ${patient.prenom}` : null;
  }
}
