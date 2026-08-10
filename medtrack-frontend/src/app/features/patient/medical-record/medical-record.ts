import { Component, computed, effect, inject, input, output, signal, TemplateRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { NgbModal, NgbModalModule, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import {
  EditOutline,
  EyeOutline,
  InboxOutline,
  RollbackOutline,
  PlusOutline,
  FileTextOutline,
  InfoCircleOutline,
  MedicineBoxOutline
} from '@ant-design/icons-angular/icons';
import { Pagination } from 'src/app/theme/shared/components/pagination/pagination';
import { MedicalRecordService } from 'src/app/core/services/medical-record.service';
import { AppointmentService } from 'src/app/core/services/appointment.service';
import { PrescriptionsModalComponent } from './prescriptions-modal/prescriptions-modal.component';
import { NotificationService } from 'src/app/core/services/notification.service';
import { AuthService } from 'src/app/core/services/auth.service';
import {
  MedicalRecord as MedicalRecordDto,
  MedicalRecordRequest,
  GroupeSanguin,
  Consultation,
  ConsultationRequest
} from 'src/app/core/models/medical-record.model';
import { UpdateAppointmentRequest } from 'src/app/core/models/appointment.model';
import { MedecinOption } from 'src/app/core/models/medecin.model';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { confirmDiscardIfDirty, focusFirstField, focusFirstInvalidField } from 'src/app/core/utils/modal-ux';
import { formatDateDisplay } from 'src/app/core/utils/date-format';
import { DateFormatPipe } from 'src/app/core/pipes/date-format.pipe';
import { DateInputComponent } from 'src/app/theme/shared/components/date-input/date-input';
import { DebounceInput } from 'src/app/theme/shared/directives/debounce-input';
import { SortableColumn, SortState } from 'src/app/theme/shared/directives/sortable-column';
import { SpinnerComponent } from 'src/app/theme/shared/components/spinner/spinner.component';
import { Spinkit } from 'src/app/theme/shared/components/spinner/spinkits';

/** Rendez-vous d'origine transmis par patient-detail (query params ?openConsultation=&appointmentId=&motif=&date=). */
export interface ConsultationPrefill {
  appointmentId: number;
  motif: string;
  date: string;
}

interface RecordFormModel {
  groupeSanguin: GroupeSanguin | null;
  allergies: string;
  antecedents: string;
  traitementsChroniques: string;
  antecedentsFamiliaux: string;
  vaccinations: string;
}

/** Modèle du formulaire de consultation : medecinId nullable tant que rien n'est sélectionné, constantes vitales nullables (non mesurées). */
interface ConsultationFormModel {
  consultationDate: string;
  motif: string;
  diagnostic: string;
  notes: string;
  medecinId: number | null;
  // Rendez-vous d'origine si la consultation est démarrée depuis /appointments, jamais saisi
  // à la main (pas de champ dans le formulaire). Ignoré par le backend en édition.
  appointmentId: number | null;
  tensionArterielleSystolique: number | null;
  tensionArterielleDiastolique: number | null;
  poids: number | null;
  temperature: number | null;
  pouls: number | null;
}

@Component({
  selector: 'app-medical-record',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    Pagination,
    NgbModalModule,
    NgbTooltip,
    TranslocoModule,
    RouterLink,
    IconDirective,
    PrescriptionsModalComponent,
    DateInputComponent,
    DateFormatPipe,
    DebounceInput,
    SortableColumn,
    SpinnerComponent
  ],
  templateUrl: './medical-record.html',
  styleUrl: './medical-record.scss'
})
export class MedicalRecord {
  readonly spinkit = Spinkit.skLine;

  private readonly medicalRecordService = inject(MedicalRecordService);
  private readonly appointmentService = inject(AppointmentService);
  private readonly notificationService = inject(NotificationService);
  private readonly modalService = inject(NgbModal);
  private readonly translocoService = inject(TranslocoService);
  private readonly iconService = inject(IconService);
  private readonly authService = inject(AuthService);
  readonly activeSection = signal<'summary' | 'consultations'>('summary');

  setActiveSection(section: 'summary' | 'consultations'): void {
    this.activeSection.set(section);
  }

  // Rôle courant : un MEDECIN ne peut choisir un médecin pour une consultation
  // (ConsultationService.resolveMedecinId le force à son propre id côté backend,
  // que la valeur soumise soit renseignée ou non) — le select n'a de sens que pour ADMIN.
  private readonly currentUser = this.authService.currentUser;
  readonly isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');

  // Patient concerné et droits d'écriture (calculés par le parent : ADMIN ou médecin référent)
  patientId = input.required<number>();
  canEdit = input(false);
  medecins = input.required<MedecinOption[]>();
  // transmis par patient-detail depuis les query params. null = arrivée normale sur la fiche.
  prefillConsultation = input<ConsultationPrefill | null>(null);
  targetConsultationId = input<number | null>(null);

  // sans second appel réseau. null quand le patient n'a pas encore de dossier (404) ou en erreur.
  recordLoaded = output<MedicalRecordDto | null>();
  // son propre signal et les query params — sans ça, un rechargement du dossier (ex. après avoir
  prefillConsumed = output<void>();
  // Échec volontairement générique : le parent possède l'URL et affiche un message identique
  targetConsultationFailed = output<void>();

  // Valeurs exactes de l'enum backend GroupeSanguin (EnumType.STRING, valeur envoyée telle quelle)
  readonly groupeSanguinOptions: GroupeSanguin[] = [
    'A_POSITIF',
    'A_NEGATIF',
    'B_POSITIF',
    'B_NEGATIF',
    'AB_POSITIF',
    'AB_NEGATIF',
    'O_POSITIF',
    'O_NEGATIF'
  ];

  private readonly groupeSanguinLabels: Record<GroupeSanguin, string> = {
    A_POSITIF: 'A+',
    A_NEGATIF: 'A-',
    B_POSITIF: 'B+',
    B_NEGATIF: 'B-',
    AB_POSITIF: 'AB+',
    AB_NEGATIF: 'AB-',
    O_POSITIF: 'O+',
    O_NEGATIF: 'O-'
  };

  record = signal<MedicalRecordDto | null>(null);
  isLoading = signal(true);
  loadError = signal<string | null>(null);
  // Distinct de loadError : 404 = pas encore de dossier, état vide normal (pas une panne)
  notFound = signal(false);

  @ViewChild('recordModal') recordModal!: TemplateRef<unknown>;
  @ViewChild('recordForm') private recordFormRef?: NgForm;
  recordMode: 'create' | 'edit' = 'create';
  recordSubmitted = signal(false);
  recordSubmitting = signal(false);
  recordModel: RecordFormModel = {
    groupeSanguin: null,
    allergies: '',
    antecedents: '',
    traitementsChroniques: '',
    antecedentsFamiliaux: '',
    vaccinations: ''
  };

  consultations = signal<Consultation[]>([]);
  focusedConsultation = signal<Consultation | null>(null);
  readonly displayedConsultations = computed(() => {
    const consultations = this.consultations();
    const focused = this.focusedConsultation();
    return focused && !consultations.some((consultation) => consultation.id === focused.id) ? [focused, ...consultations] : consultations;
  });
  consultationsLoading = signal(true);
  consultationsError = signal<string | null>(null);
  page = signal(0);
  size = signal(10);
  totalPages = signal(0);
  totalElements = signal(0);
  /**
   * Filtre exact (pas "inclusif") côté backend : true renvoie UNIQUEMENT les consultations
   * archivées, jamais archivées + actives en même temps (voir ConsultationFilters.archived).
   */
  showArchivedFilter = signal(false);
  /** Recherche backend multi-mots : motif OU diagnostic (voir ConsultationSpecifications.hasSearchTerm). */
  searchInput = signal('');
  searchTerm = signal('');
  /** null = tri par défaut du backend (createdAt desc), pas d'en-tête surligné. */
  sortState = signal<SortState | null>(null);

  @ViewChild('consultationModal') consultationModal!: TemplateRef<unknown>;
  @ViewChild('consultationForm') private consultationFormRef?: NgForm;
  consultationMode: 'create' | 'edit' = 'create';
  consultationSubmitted = signal(false);
  consultationSubmitting = signal(false);
  private editingConsultationId: number | null = null;
  consultationModel: ConsultationFormModel = {
    consultationDate: '',
    motif: '',
    diagnostic: '',
    notes: '',
    medecinId: null,
    appointmentId: null,
    tensionArterielleSystolique: null,
    tensionArterielleDiastolique: null,
    poids: null,
    temperature: null,
    pouls: null
  };

  // --- Modal de confirmation d'archivage/restauration ---
  @ViewChild('confirmArchiveModal') confirmArchiveModal!: TemplateRef<unknown>;
  pendingConsultation: Consultation | null = null;
  pendingConsultationAction: 'archive' | 'restore' = 'archive';

  @ViewChild('consultationViewModal') consultationViewModal!: TemplateRef<unknown>;
  viewedConsultation = signal<Consultation | null>(null);

  @ViewChild(PrescriptionsModalComponent) prescriptionsModalCmp!: PrescriptionsModalComponent;

  constructor() {
    this.iconService.addIcon(
      EditOutline,
      EyeOutline,
      InboxOutline,
      RollbackOutline,
      PlusOutline,
      FileTextOutline,
      InfoCircleOutline,
      MedicineBoxOutline
    );

    effect(() => {
      this.patientId();
      this.activeSection.set('summary');
      this.page.set(0);
      this.showArchivedFilter.set(false);
      this.searchInput.set('');
      this.searchTerm.set('');
      this.sortState.set(null);
      this.loadRecord();
    });

    effect(() => {
      const prefill = this.prefillConsultation();
      if (!prefill || !this.record() || !this.canEdit()) {
        return;
      }
      this.activeSection.set('consultations');
      this.openCreateConsultation(prefill);
      this.prefillConsumed.emit();
    });

    // page courante. La vérification medicalRecordId empêche d'afficher une consultation dans
    // le mauvais dossier si l'URL patient/consultation a été assemblée incorrectement.
    effect(() => {
      const consultationId = this.targetConsultationId();
      const record = this.record();
      if (consultationId == null || !record) {
        this.focusedConsultation.set(null);
        return;
      }
      this.activeSection.set('consultations');
      this.loadFocusedConsultation(consultationId, record.id);
    });
  }

  private loadFocusedConsultation(consultationId: number, medicalRecordId: number): void {
    this.medicalRecordService.getConsultationById(consultationId).subscribe({
      next: (consultation) => {
        if (consultation.medicalRecordId !== medicalRecordId) {
          this.focusedConsultation.set(null);
          this.activeSection.set('summary');
          this.targetConsultationFailed.emit();
          return;
        }
        this.focusedConsultation.set(consultation);
      },
      error: () => {
        this.focusedConsultation.set(null);
        this.activeSection.set('summary');
        this.targetConsultationFailed.emit();
      }
    });
  }

  /**
   * Charge le dossier médical du patient. 404 -> état vide (notFound), pas une erreur ;
   * toute autre erreur HTTP -> message affiché via loadError.
   */
  loadRecord(): void {
    this.isLoading.set(true);
    this.loadError.set(null);
    this.notFound.set(false);

    this.medicalRecordService.getByPatient(this.patientId()).subscribe({
      next: (data) => {
        this.record.set(data);
        this.recordLoaded.emit(data);
        this.isLoading.set(false);
        this.loadConsultations();
      },
      error: (err) => {
        this.isLoading.set(false);
        if (err.status === 404) {
          this.notFound.set(true);
          this.record.set(null);
          this.recordLoaded.emit(null);
          this.consultationsLoading.set(false);
          return;
        }
        this.recordLoaded.emit(null);
        this.loadError.set(
          this.translocoService.translate(
            err.status === 401
              ? 'medicalRecord.errors.unauthorized'
              : err.status === 403
                ? 'medicalRecord.errors.forbidden'
                : 'medicalRecord.errors.loadFailed'
          )
        );
      }
    });
  }

  groupeSanguinLabel(value: GroupeSanguin): string {
    return this.groupeSanguinLabels[value];
  }

  openCreateRecordModal(): void {
    this.recordMode = 'create';
    this.recordModel = {
      groupeSanguin: null,
      allergies: '',
      antecedents: '',
      traitementsChroniques: '',
      antecedentsFamiliaux: '',
      vaccinations: ''
    };
    this.recordSubmitted.set(false);
    this.openRecordModal();
  }

  openEditRecordModal(): void {
    const r = this.record();
    if (!r) return;

    this.recordMode = 'edit';
    this.recordModel = {
      groupeSanguin: r.groupeSanguin,
      allergies: r.allergies,
      antecedents: r.antecedents,
      traitementsChroniques: r.traitementsChroniques ?? '',
      antecedentsFamiliaux: r.antecedentsFamiliaux ?? '',
      vaccinations: r.vaccinations ?? ''
    };
    this.recordSubmitted.set(false);
    this.openRecordModal();
  }

  private openRecordModal(): void {
    const modalRef = this.modalService.open(this.recordModal, {
      centered: true,
      beforeDismiss: () =>
        confirmDiscardIfDirty(this.recordFormRef ?? { dirty: false }, this.translocoService.translate('common.modal.confirmDiscard'))
    });
    focusFirstField(modalRef, 'recordGroupeSanguin');
  }

  /**
   * Soumission du formulaire de dossier médical (création ou édition selon `recordMode`).
   * Validation cliente AVANT tout appel réseau : les 3 champs sont obligatoires côté
   * backend (@Valid MedicalRecordRequest), un envoi incomplet renvoie systématiquement
   * un 400 — le formulaire bloque donc la soumission tant qu'il est invalide.
   */
  onSubmitRecord(form: NgForm, modal: { close: () => void }): void {
    this.recordSubmitted.set(true);

    if (form.invalid || this.recordModel.groupeSanguin == null) {
      focusFirstInvalidField();
      return;
    }

    const request: MedicalRecordRequest = {
      groupeSanguin: this.recordModel.groupeSanguin,
      allergies: this.recordModel.allergies.trim(),
      antecedents: this.recordModel.antecedents.trim(),
      traitementsChroniques: this.recordModel.traitementsChroniques.trim() || null,
      antecedentsFamiliaux: this.recordModel.antecedentsFamiliaux.trim() || null,
      vaccinations: this.recordModel.vaccinations.trim() || null
    };

    this.recordSubmitting.set(true);

    const isCreate = this.recordMode === 'create';
    const request$ = isCreate
      ? this.medicalRecordService.create(this.patientId(), request)
      : this.medicalRecordService.update(this.patientId(), request);

    request$.subscribe({
      next: (data) => {
        this.recordSubmitting.set(false);
        modal.close();
        this.record.set(data);
        this.recordLoaded.emit(data);
        this.notificationService.showSuccess(
          this.translocoService.translate(isCreate ? 'medicalRecord.success.recordCreated' : 'medicalRecord.success.recordUpdated')
        );
        if (isCreate) {
          this.notFound.set(false);
          this.loadConsultations();
        }
      },
      error: (err) => {
        this.recordSubmitting.set(false);
        this.notificationService.showError(
          extractBackendErrorMessage(err) ??
            this.translocoService.translate(
              err.status === 403
                ? isCreate
                  ? 'medicalRecord.errors.actionForbidden'
                  : 'medicalRecord.errors.updateForbidden'
                : err.status === 400
                  ? 'medicalRecord.errors.invalidData'
                  : isCreate
                    ? 'medicalRecord.errors.createFailed'
                    : 'medicalRecord.errors.updateFailed'
            )
        );
      }
    });
  }

  /** Charge la page courante de consultations du dossier (tri createdAt DESC fixé côté backend). */
  loadConsultations(): void {
    const r = this.record();
    if (!r) return;

    this.consultationsLoading.set(true);
    this.consultationsError.set(null);

    this.medicalRecordService
      .getConsultations(r.id, this.page(), this.size(), {
        archived: this.showArchivedFilter(),
        q: this.searchTerm() || undefined,
        sort: this.sortState()?.field,
        direction: this.sortState()?.direction
      })
      .subscribe({
        next: (data) => {
          this.consultations.set(data.content);
          this.totalPages.set(data.totalPages);
          this.totalElements.set(data.totalElements);
          this.consultationsLoading.set(false);
        },
        error: (err) => {
          this.consultationsError.set(
            this.translocoService.translate(
              err.status === 403 ? 'medicalRecord.errors.forbidden' : 'medicalRecord.errors.consultationsLoadFailed'
            )
          );
          this.consultationsLoading.set(false);
        }
      });
  }

  onPageChange(newPage: number): void {
    this.page.set(newPage);
    this.loadConsultations();
  }

  onSizeChange(newSize: number): void {
    this.size.set(newSize);
    this.page.set(0);
    this.loadConsultations();
  }

  /** Toggle "Afficher les archivées" : application immédiate, retour à la page 0. */
  onShowArchivedFilterChange(value: boolean): void {
    this.showArchivedFilter.set(value);
    this.page.set(0);
    this.loadConsultations();
  }

  onConsultationsSearchChange(term: string): void {
    this.searchTerm.set(term);
    this.page.set(0);
    this.loadConsultations();
  }

  onConsultationsSortChange(sort: SortState): void {
    this.sortState.set(sort);
    this.page.set(0);
    this.loadConsultations();
  }

  medecinDisplay(medecinId: number): string {
    const med = this.medecins().find((m) => Number(m.id) === Number(medecinId));
    return med ? `${med.nom} ${med.prenom}` : `Médecin #${medecinId}`;
  }

  vitalsSummary(c: Consultation): string {
    const parts: string[] = [];
    if (c.tensionArterielleSystolique != null && c.tensionArterielleDiastolique != null) {
      parts.push(`${c.tensionArterielleSystolique}/${c.tensionArterielleDiastolique} mmHg`);
    }
    if (c.poids != null) parts.push(`${c.poids} kg`);
    if (c.temperature != null) parts.push(`${c.temperature} °C`);
    if (c.pouls != null) parts.push(`${c.pouls} bpm`);
    return parts.length ? parts.join(' · ') : '—';
  }

  tensionDisplay(c: Consultation): string {
    if (c.tensionArterielleSystolique == null || c.tensionArterielleDiastolique == null) return '—';
    return `${c.tensionArterielleSystolique}/${c.tensionArterielleDiastolique} mmHg`;
  }

  /**
   * Bouton "Voir détail" de la liste : ouvre la fiche en lecture seule directement, sans
   * navigation ni rechargement (les données de la ligne sont déjà en mémoire). Un clic avec
   * modificateur (ouverture en nouvel onglet) suit en revanche le lien normalement, pour garder
   * l'URL ?consultationId= partageable en deep-link.
   */
  onViewConsultationClick(event: MouseEvent, c: Consultation): void {
    if (event.ctrlKey || event.metaKey || event.shiftKey || event.button !== 0) {
      return;
    }
    event.preventDefault();
    this.viewedConsultation.set(c);
    this.modalService.open(this.consultationViewModal, { centered: true, size: 'lg' });
  }

  /**
   * Médecin à préremplir dans le formulaire de consultation : pour un MEDECIN,
   * toujours son propre id (le backend l'impose de toute façon, cf. resolveMedecinId) ;
   * pour un ADMIN, la valeur fournie (null en création, celle de la consultation en édition).
   */
  private resolveFormMedecinId(existing: number | null): number | null {
    if (this.isAdmin()) return existing;
    return this.currentUser()?.id ?? null;
  }

  openCreateConsultation(prefill?: ConsultationPrefill): void {
    this.consultationMode = 'create';
    this.editingConsultationId = null;
    this.consultationModel = {
      consultationDate: prefill?.date ?? '',
      motif: prefill?.motif ?? '',
      diagnostic: '',
      notes: '',
      medecinId: this.resolveFormMedecinId(null),
      appointmentId: prefill?.appointmentId ?? null,
      tensionArterielleSystolique: null,
      tensionArterielleDiastolique: null,
      poids: null,
      temperature: null,
      pouls: null
    };
    this.consultationSubmitted.set(false);
    this.openConsultationModal();
  }

  openEditConsultation(c: Consultation): void {
    this.consultationMode = 'edit';
    this.editingConsultationId = c.id;
    this.consultationModel = {
      consultationDate: c.consultationDate,
      motif: c.motif,
      diagnostic: c.diagnostic,
      notes: c.notes ?? '',
      medecinId: this.resolveFormMedecinId(c.medecinId),
      appointmentId: c.appointmentId ?? null,
      tensionArterielleSystolique: c.tensionArterielleSystolique ?? null,
      tensionArterielleDiastolique: c.tensionArterielleDiastolique ?? null,
      poids: c.poids ?? null,
      temperature: c.temperature ?? null,
      pouls: c.pouls ?? null
    };
    this.consultationSubmitted.set(false);
    this.openConsultationModal();
  }

  private openConsultationModal(): void {
    const modalRef = this.modalService.open(this.consultationModal, {
      centered: true,
      beforeDismiss: () =>
        confirmDiscardIfDirty(this.consultationFormRef ?? { dirty: false }, this.translocoService.translate('common.modal.confirmDiscard'))
    });
    focusFirstField(modalRef, 'consultationDate');
  }

  onSubmitConsultation(form: NgForm, modal: { close: () => void }): void {
    this.consultationSubmitted.set(true);

    if (form.invalid || this.consultationModel.medecinId == null) {
      focusFirstInvalidField();
      return;
    }

    const r = this.record();
    if (!r) return;

    const request: ConsultationRequest = {
      consultationDate: this.consultationModel.consultationDate,
      motif: this.consultationModel.motif.trim(),
      diagnostic: this.consultationModel.diagnostic.trim(),
      notes: this.consultationModel.notes?.trim() || null,
      medecinId: this.consultationModel.medecinId,
      appointmentId: this.consultationModel.appointmentId,
      tensionArterielleSystolique: this.consultationModel.tensionArterielleSystolique,
      tensionArterielleDiastolique: this.consultationModel.tensionArterielleDiastolique,
      poids: this.consultationModel.poids,
      temperature: this.consultationModel.temperature,
      pouls: this.consultationModel.pouls
    };

    this.consultationSubmitting.set(true);

    const isCreate = this.consultationMode === 'create';
    const request$ = isCreate
      ? this.medicalRecordService.createConsultation(r.id, request)
      : this.medicalRecordService.updateConsultation(this.editingConsultationId!, request);

    request$.subscribe({
      next: () => {
        this.consultationSubmitting.set(false);
        modal.close();
        this.notificationService.showSuccess(
          this.translocoService.translate(
            isCreate ? 'medicalRecord.success.consultationAdded' : 'medicalRecord.success.consultationUpdated'
          )
        );
        if (isCreate) {
          // Tri createdAt DESC (fixé côté backend) : la nouvelle consultation n'est visible qu'en page 0
          this.page.set(0);
        }
        this.loadConsultations();
        if (isCreate && this.consultationModel.appointmentId != null) {
          this.markAppointmentHonored(this.consultationModel.appointmentId);
        }
      },
      error: (err) => {
        this.consultationSubmitting.set(false);
        this.notificationService.showError(
          extractBackendErrorMessage(err) ??
            this.translocoService.translate(
              err.status === 403
                ? 'medicalRecord.errors.actionForbidden'
                : err.status === 400
                  ? 'medicalRecord.errors.invalidData'
                  : 'medicalRecord.errors.consultationSaveFailed'
            )
        );
      }
    });
  }

  /**
   * Passe automatiquement le rendez-vous d'origine en HONORE après l'enregistrement d'une
   * consultation démarrée depuis lui. Best-effort : un échec ici ne remet pas en cause la
   * consultation déjà enregistrée, le rendez-vous reste modifiable manuellement depuis
   * /appointments si besoin.
   */
  private markAppointmentHonored(appointmentId: number): void {
    this.appointmentService.getAppointmentById(appointmentId).subscribe({
      next: (appt) => {
        if (appt.statut === 'HONORE') {
          return;
        }
        const request: UpdateAppointmentRequest = {
          medecinId: appt.medecinId,
          dateHeure: appt.dateHeure,
          motif: appt.motif,
          statut: 'HONORE',
          notes: appt.notes
        };
        this.appointmentService.updateAppointment(appointmentId, request).subscribe({
          next: () =>
            this.notificationService.showSuccess(this.translocoService.translate('medicalRecord.consultations.appointmentMarkedHonored')),
          error: () => {
            /* best-effort : voir le commentaire de la méthode */
          }
        });
      },
      error: () => {
        /* best-effort : voir le commentaire de la méthode */
      }
    });
  }

  /**
   * Archive ou restaure une consultation selon son état actuel (même pattern
   * que PatientDetailComponent.onArchiveToggle).
   */
  onArchiveToggleConsultation(c: Consultation): void {
    this.pendingConsultation = c;
    this.pendingConsultationAction = c.archived ? 'restore' : 'archive';
    this.modalService.open(this.confirmArchiveModal, { centered: true, backdrop: 'static' }).result.then(
      (result) => {
        if (result !== 'confirm') {
          return;
        }

        const request$ = c.archived
          ? this.medicalRecordService.restoreConsultation(c.id)
          : this.medicalRecordService.archiveConsultation(c.id);

        request$.subscribe({
          next: () => {
            this.notificationService.showSuccess(
              this.translocoService.translate(
                c.archived ? 'medicalRecord.success.consultationRestored' : 'medicalRecord.success.consultationArchived'
              )
            );
            this.loadConsultations();
          },
          error: (err) => {
            const forbiddenKey =
              this.pendingConsultationAction === 'restore'
                ? 'medicalRecord.errors.restoreForbidden'
                : 'medicalRecord.errors.archiveForbidden';
            const failedKey =
              this.pendingConsultationAction === 'restore' ? 'medicalRecord.errors.restoreFailed' : 'medicalRecord.errors.archiveFailed';
            this.notificationService.showError(
              extractBackendErrorMessage(err) ?? this.translocoService.translate(err.status === 403 ? forbiddenKey : failedKey)
            );
          }
        });
      },
      () => {}
    );
  }

  onOpenPrescriptions(c: Consultation): void {
    this.prescriptionsModalCmp.open(c);
  }

  traceabilityLine(
    createdBy?: string | null,
    createdAt?: string | null,
    modifiedBy?: string | null,
    updatedAt?: string | null
  ): string | null {
    const parts: string[] = [];
    if (createdBy || createdAt) {
      parts.push(
        this.translocoService.translate('medicalRecord.traceability.createdBy', {
          user: createdBy ?? '—',
          date: formatDateDisplay(createdAt)
        })
      );
    }
    if (modifiedBy || updatedAt) {
      parts.push(
        this.translocoService.translate('medicalRecord.traceability.modifiedBy', {
          user: modifiedBy ?? '—',
          date: formatDateDisplay(updatedAt)
        })
      );
    }
    return parts.length ? parts.join(' · ') : null;
  }

  consultationTraceability(c: Consultation): string {
    return this.traceabilityLine(c.createdBy, c.createdAt, c.modifiedBy, c.updatedAt) ?? '';
  }

  get today(): string {
    return new Date().toISOString().split('T')[0];
  }
}
