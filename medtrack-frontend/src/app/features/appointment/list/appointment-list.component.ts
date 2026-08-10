import { Component, OnInit, ViewChild, TemplateRef, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule, NgForm } from '@angular/forms';
import { NgbDateStruct, NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import { CalendarOutline, DeleteOutline, PlusOutline, UnorderedListOutline } from '@ant-design/icons-angular/icons';
import { CardComponent } from 'src/app/theme/shared/components/card/card.component';
import { DebounceInput } from 'src/app/theme/shared/directives/debounce-input';
import { AdvancedFilters, FilterChip } from 'src/app/theme/shared/components/advanced-filters/advanced-filters';
import { DateInputComponent } from 'src/app/theme/shared/components/date-input/date-input';
import { isDateRangeInvalid } from 'src/app/core/utils/date-range';
import { isMobileViewport } from 'src/app/core/utils/viewport';
import { AppointmentService, AppointmentFilterParams } from 'src/app/core/services/appointment.service';
import { PatientService } from 'src/app/core/services/patient.service';
import { MedecinService } from 'src/app/core/services/medecin.service';
import { MedicalRecordService } from 'src/app/core/services/medical-record.service';
import { AuthService } from 'src/app/core/services/auth.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import {
  Appointment,
  AppointmentStatut,
  CreateAppointmentRequest,
  PatientOption,
  UpdateAppointmentRequest
} from 'src/app/core/models/appointment.model';
import { MedecinOption } from 'src/app/core/models/medecin.model';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { confirmDiscardIfDirty, focusFirstField, focusFirstInvalidField } from 'src/app/core/utils/modal-ux';
import { formatDateDisplay } from 'src/app/core/utils/date-format';
import { AppointmentCalendarComponent } from '../calendar/appointment-calendar.component';
import { DateTimeFormatPipe } from 'src/app/core/pipes/date-time-format.pipe';
import { AppointmentStatutBadgeComponent } from 'src/app/theme/shared/components/appointment-statut-badge/appointment-statut-badge';
import {
  AppointmentStatusChange,
  AppointmentTableComponent
} from 'src/app/features/appointment/components/appointment-table/appointment-table.component';
import { SortState } from 'src/app/theme/shared/directives/sortable-column';

// sont séparées : l'heure est choisie parmi les créneaux libres du backend, pas saisie librement.
interface AppointmentFormModel {
  patientId: number | null;
  medecinId: number | null;
  date: string; // ISO "YYYY-MM-DD", voir app-date-input
  heure: string; // créneau sélectionné : "HH:mm"
  motif: string;
  statut: AppointmentStatut;
  notes: string;
}

@Component({
  selector: 'app-appointment-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CardComponent,
    DebounceInput,
    AdvancedFilters,
    DateInputComponent,
    NgbModalModule,
    TranslocoModule,
    RouterLink,
    IconDirective,
    AppointmentCalendarComponent,
    AppointmentTableComponent,
    AppointmentStatutBadgeComponent,
    DateTimeFormatPipe
  ],
  templateUrl: './appointment-list.component.html',
  styleUrl: './appointment-list.component.scss'
})
export class AppointmentListComponent implements OnInit {
  private readonly appointmentService = inject(AppointmentService);
  private readonly patientService = inject(PatientService);
  private readonly medecinService = inject(MedecinService);
  private readonly medicalRecordService = inject(MedicalRecordService);
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly translocoService = inject(TranslocoService);
  private readonly modalService = inject(NgbModal);
  private readonly iconService = inject(IconService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  @ViewChild('appointmentModal') appointmentModal!: TemplateRef<unknown>;
  @ViewChild('appointmentViewModal') appointmentViewModal!: TemplateRef<unknown>;
  // dans beforeDismiss, jamais au moment de l'appel à modalService.open().
  @ViewChild('appointmentForm') private appointmentFormRef?: NgForm;
  @ViewChild('confirmDeleteModal') confirmDeleteModal!: TemplateRef<unknown>;
  @ViewChild(AppointmentCalendarComponent) calendarComponent?: AppointmentCalendarComponent;

  readonly statutOptions: AppointmentStatut[] = ['PLANIFIE', 'CONFIRME', 'ANNULE', 'HONORE'];

  viewMode = signal<'list' | 'calendar'>('list');

  private readonly currentUser = this.authService.currentUser;
  readonly isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');

  appointments = signal<Appointment[]>([]);
  isLoading = signal(true);
  error = signal<string | null>(null);
  page = signal(0);
  size = signal(10);
  totalPages = signal(0);
  totalElements = signal(0);
  /** null = tri par défaut du backend (dateHeure asc), pas d'en-tête surligné. */
  sortState = signal<SortState | null>(null);
  viewedAppointment = signal<Appointment | null>(null);
  appointmentViewLoading = signal(false);
  appointmentViewError = signal<string | null>(null);
  private viewedAppointmentId: number | null = null;

  // Recherche rapide : q (nom/prénom patient OU motif, voir AppointmentFilter côté backend).
  searchInput = signal('');
  searchTerm = signal('');

  // Panneau de recherche avancée : statut, plage de dates du rendez-vous, médecin (ADMIN
  // uniquement). Selects/dates à application immédiate (pas de debounce).
  statutFilter = signal<AppointmentStatut | ''>('');
  dateFromFilter = signal('');
  dateToFilter = signal('');
  readonly dateRangeInvalid = computed(() => isDateRangeInvalid(this.dateFromFilter(), this.dateToFilter()));
  medecinIdFilter = signal<number | null>(null);

  /** Ouvert par défaut seulement si l'URL arrive déjà avec des filtres du panneau (voir ngOnInit). */
  filtersPanelOpen = signal(false);

  readonly activeFilterCount = computed(() => {
    let count = 0;
    if (this.statutFilter()) count++;
    if (this.dateFromFilter()) count++;
    if (this.dateToFilter()) count++;
    if (this.medecinIdFilter() != null) count++;
    return count;
  });

  readonly filterChips = computed<FilterChip[]>(() => {
    const chips: FilterChip[] = [];
    if (this.statutFilter())
      chips.push({
        key: 'statut',
        label: this.translocoService.translate('appointment.list.filters.chips.statut'),
        value: this.translocoService.translate('appointment.statut.' + this.statutFilter())
      });
    if (this.dateFromFilter())
      chips.push({
        key: 'dateFrom',
        label: this.translocoService.translate('appointment.list.filters.chips.dateFrom'),
        value: formatDateDisplay(this.dateFromFilter())
      });
    if (this.dateToFilter())
      chips.push({
        key: 'dateTo',
        label: this.translocoService.translate('appointment.list.filters.chips.dateTo'),
        value: formatDateDisplay(this.dateToFilter())
      });
    const medecinId = this.medecinIdFilter();
    if (medecinId != null) {
      const m = this.medecins().find((x) => x.id === medecinId);
      chips.push({
        key: 'medecin',
        label: this.translocoService.translate('appointment.list.filters.chips.medecin'),
        value: m ? `${m.nom} ${m.prenom}` : `#${medecinId}`
      });
    }
    return chips;
  });

  // --- Médecins (ADMIN uniquement) pour le select du formulaire ET le filtre du panneau, best-effort ---
  medecins = signal<MedecinOption[]>([]);

  modalMode: 'create' | 'edit' = 'create';
  private editingId: number | null = null;
  formSubmitted = signal(false);
  formSubmitting = signal(false);
  appointmentModel: AppointmentFormModel = {
    patientId: null,
    medecinId: null,
    date: '',
    heure: '',
    motif: '',
    statut: 'PLANIFIE',
    notes: ''
  };

  // changement de médecin ou de date. Le pas et les heures d'ouverture viennent du backend.
  creneaux = signal<string[]>([]);
  creneauxLoading = signal(false);
  creneauxError = signal(false);

  // Créneau du rendez-vous en cours d'édition : le backend ne l'exclut pas de la liste des
  private editingDate = '';
  private editingHeure = '';

  // Recherche de patient (création uniquement : le patient d'un rendez-vous existant n'est
  // plus modifiable, cf. AppointmentService.update qui ignore patientId en mise à jour).
  patientSearchInput = signal('');
  patientSearchResults = signal<PatientOption[]>([]);
  patientSearchLoading = signal(false);
  selectedPatientLabel = signal('');

  pendingAppointment: Appointment | null = null;

  // Rendez-vous à mettre en évidence (clic sur une notification, query param appointmentId).
  highlightedAppointmentId = signal<number | null>(null);

  // boutons le temps de l'appel pour éviter les doubles clics.
  statusUpdatingId = signal<number | null>(null);

  consultationLookupId = signal<number | null>(null);
  consultationUnavailableIds = signal<ReadonlySet<number>>(new Set<number>());

  constructor() {
    this.iconService.addIcon(PlusOutline, DeleteOutline, UnorderedListOutline, CalendarOutline);
  }

  ngOnInit(): void {
    if (this.isAdmin()) {
      this.medecinService.getAllMedecinOptions().subscribe({
        next: (options) => this.medecins.set(options),
        error: () => {
          /* best-effort : le select médecin du formulaire restera vide en cas d'échec */
        }
      });
    }

    // Arrivée ciblée depuis un autre écran : ?appointmentId=123 (notification) ou ?date=YYYY-MM-DD
    // (carte "RDV du jour" du dashboard). queryParamMap rejoue la valeur courante de façon
    // synchrone, donc `handled` est déjà positionné avant le if plus bas, ce qui évite un double
    // chargement. Ces deux params sont consommés puis retirés de l'URL ; sinon on relit les
    // filtres du panneau déjà présents dans l'URL (F5, retour arrière, lien partagé).
    let handled = false;
    this.route.queryParamMap.subscribe((params) => {
      const idParam = params.get('appointmentId');
      const dateParam = params.get('date');
      if (idParam) {
        handled = true;
        this.focusAppointment(+idParam);
      } else if (dateParam) {
        handled = true;
        this.viewMode.set('list');
        this.dateFromFilter.set(dateParam);
        this.dateToFilter.set(dateParam);
        this.resetToFirstPage();
        this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
      }
    });

    if (!handled) {
      const params = this.route.snapshot.queryParamMap;
      if (params.get('statut')) this.statutFilter.set(params.get('statut') as AppointmentStatut);
      if (params.get('dateFrom')) this.dateFromFilter.set(params.get('dateFrom')!);
      if (params.get('dateTo')) this.dateToFilter.set(params.get('dateTo')!);
      if (params.get('medecinId')) this.medecinIdFilter.set(Number(params.get('medecinId')));
      if (params.get('sort') && (params.get('direction') === 'asc' || params.get('direction') === 'desc')) {
        this.sortState.set({ field: params.get('sort')!, direction: params.get('direction') as 'asc' | 'desc' });
      }
      if (this.activeFilterCount() > 0) this.filtersPanelOpen.set(true);
      this.loadAppointments();
    }
  }

  // Reflète les filtres du panneau dans l'URL (F5, retour arrière, lien partagé). Appelée par les
  // handlers du panneau et pas depuis loadAppointments, sinon ça re-remplirait l'URL juste après
  // que ngOnInit/focusAppointment l'aient vidée pour une arrivée ciblée ?appointmentId=/?date=.
  private syncFiltersToUrl(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        statut: this.statutFilter() || null,
        dateFrom: this.dateFromFilter() || null,
        dateTo: this.dateToFilter() || null,
        medecinId: this.medecinIdFilter(),
        sort: this.sortState()?.field || null,
        direction: this.sortState()?.direction || null
      },
      replaceUrl: true
    });
  }

  // Cale les filtres de date sur le jour du rendez-vous ciblé pour le faire apparaître en page 0,
  // puis le met en évidence. Best-effort : rendez-vous introuvable ou inaccessible -> pas de highlight.
  private focusAppointment(id: number): void {
    this.appointmentService.getAppointmentById(id).subscribe({
      next: (appt) => {
        this.highlightedAppointmentId.set(appt.id);
        this.viewMode.set('list');
        const day = appt.dateHeure.slice(0, 10);
        this.dateFromFilter.set(day);
        this.dateToFilter.set(day);
        this.resetToFirstPage();
        // Retire appointmentId de l'URL une fois consommé : un retour ultérieur sur
        // /appointments ne doit pas re-déclencher le même filtrage.
        this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
      },
      error: () => {}
    });
  }

  setViewMode(mode: 'list' | 'calendar'): void {
    this.viewMode.set(mode);
  }

  loadAppointments(): void {
    this.isLoading.set(true);
    this.error.set(null);
    this.consultationUnavailableIds.set(new Set<number>());

    const filters: AppointmentFilterParams = {};
    if (this.searchTerm()) filters.q = this.searchTerm();
    if (this.statutFilter()) filters.statut = this.statutFilter() as AppointmentStatut;
    if (this.dateFromFilter()) filters.dateFrom = `${this.dateFromFilter()}T00:00:00`;
    if (this.dateToFilter() && !this.dateRangeInvalid()) filters.dateTo = `${this.dateToFilter()}T23:59:59`;
    if (this.medecinIdFilter() != null) filters.medecinId = this.medecinIdFilter()!;
    if (this.sortState()) {
      filters.sort = this.sortState()!.field;
      filters.direction = this.sortState()!.direction;
    }

    this.appointmentService.getAppointments(this.page(), this.size(), filters).subscribe({
      next: (data) => {
        this.appointments.set(data.content);
        this.totalPages.set(data.totalPages);
        this.totalElements.set(data.totalElements);
        this.isLoading.set(false);
      },
      error: (err) => {
        // Priorité au message du backend (ou timeout de l'intercepteur), sinon message générique.
        this.error.set(
          extractBackendErrorMessage(err) ??
            this.translocoService.translate(
              err.status === 401
                ? 'appointment.errors.unauthorized'
                : err.status === 403
                  ? 'appointment.errors.forbidden'
                  : 'appointment.errors.loadFailed'
            )
        );
        this.isLoading.set(false);
      }
    });
  }

  onPageChange(newPage: number): void {
    this.page.set(newPage);
    this.loadAppointments();
  }

  onSizeChange(newSize: number): void {
    this.size.set(newSize);
    this.page.set(0);
    this.loadAppointments();
  }

  private resetToFirstPage(): void {
    this.page.set(0);
    this.loadAppointments();
  }

  onSearchChange(term: string): void {
    this.searchTerm.set(term);
    this.resetToFirstPage();
  }

  onSortChange(sort: SortState): void {
    this.sortState.set(sort);
    this.resetToFirstPage();
    this.syncFiltersToUrl();
  }

  private applyPanelFilters(): void {
    if (isMobileViewport()) return;
    this.resetToFirstPage();
    this.syncFiltersToUrl();
  }

  onApplyPanelFilters(): void {
    this.resetToFirstPage();
    this.syncFiltersToUrl();
  }

  onStatutFilterChange(value: string): void {
    this.statutFilter.set(value as AppointmentStatut | '');
    this.applyPanelFilters();
  }

  onDateFromFilterChange(value: string): void {
    this.dateFromFilter.set(value);
    this.applyPanelFilters();
  }

  onDateToFilterChange(value: string): void {
    this.dateToFilter.set(value);
    this.applyPanelFilters();
  }

  onMedecinFilterChange(value: number | null): void {
    this.medecinIdFilter.set(value);
    this.applyPanelFilters();
  }

  onFilterChipRemove(key: string): void {
    switch (key) {
      case 'statut':
        this.statutFilter.set('');
        break;
      case 'dateFrom':
        this.dateFromFilter.set('');
        break;
      case 'dateTo':
        this.dateToFilter.set('');
        break;
      case 'medecin':
        this.medecinIdFilter.set(null);
        break;
    }
    this.resetToFirstPage();
    this.syncFiltersToUrl();
  }

  onResetAllFilters(): void {
    this.searchInput.set('');
    this.searchTerm.set('');
    this.statutFilter.set('');
    this.dateFromFilter.set('');
    this.dateToFilter.set('');
    this.medecinIdFilter.set(null);
    this.resetToFirstPage();
    this.syncFiltersToUrl();
  }

  onPatientSearchChange(term: string): void {
    this.appointmentModel.patientId = null;
    this.selectedPatientLabel.set('');

    if (!term.trim()) {
      this.patientSearchResults.set([]);
      return;
    }

    this.patientSearchLoading.set(true);
    this.patientService.getPatients(0, 8, { q: term }).subscribe({
      next: (data) => {
        this.patientSearchResults.set(data.content.map((p) => ({ id: p.id, nom: p.nom, prenom: p.prenom })));
        this.patientSearchLoading.set(false);
      },
      error: () => {
        this.patientSearchResults.set([]);
        this.patientSearchLoading.set(false);
      }
    });
  }

  onPatientSearchBlur(): void {
    setTimeout(() => this.patientSearchResults.set([]), 150);
  }

  selectPatient(p: PatientOption): void {
    this.appointmentModel.patientId = p.id;
    this.selectedPatientLabel.set(`${p.nom} ${p.prenom}`);
    this.patientSearchInput.set(`${p.nom} ${p.prenom}`);
    this.patientSearchResults.set([]);
  }

  /** Date du jour (locale) pour verrouiller le minimum du sélecteur de date en création. */
  get todayLocalDate(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }

  openViewModal(appointment: Appointment): void {
    this.viewedAppointmentId = appointment.id;
    this.viewedAppointment.set(null);
    this.appointmentViewError.set(null);
    this.modalService.open(this.appointmentViewModal, { centered: true, size: 'lg' });
    this.loadAppointmentView();
  }

  loadAppointmentView(): void {
    if (this.viewedAppointmentId == null) {
      return;
    }

    this.appointmentViewLoading.set(true);
    this.appointmentViewError.set(null);
    this.appointmentService.getAppointmentById(this.viewedAppointmentId).subscribe({
      next: (appointment) => {
        this.viewedAppointment.set(appointment);
        this.appointmentViewLoading.set(false);
      },
      error: (err) => {
        this.appointmentViewError.set(
          this.translocoService.translate(err.status === 404 ? 'appointment.errors.notFound' : 'appointment.errors.loadFailed')
        );
        this.appointmentViewLoading.set(false);
      }
    });
  }

  openCreateModal(prefillDate?: string): void {
    this.modalMode = 'create';
    this.editingId = null;
    this.editingDate = '';
    this.editingHeure = '';
    this.appointmentModel = {
      patientId: null,
      medecinId: this.isAdmin() ? null : (this.currentUser()?.id ?? null),
      date: prefillDate ?? '',
      heure: '',
      motif: '',
      statut: 'PLANIFIE',
      notes: ''
    };
    this.patientSearchInput.set('');
    this.patientSearchResults.set([]);
    this.selectedPatientLabel.set('');
    this.formSubmitted.set(false);
    this.loadCreneaux();
    this.openAppointmentModal();
  }

  openEditModal(appt: Appointment): void {
    this.modalMode = 'edit';
    this.editingId = appt.id;
    this.editingDate = appt.dateHeure.slice(0, 10);
    this.editingHeure = appt.dateHeure.slice(11, 16);
    this.appointmentModel = {
      patientId: appt.patientId,
      medecinId: this.isAdmin() ? appt.medecinId : (this.currentUser()?.id ?? null),
      date: this.editingDate,
      heure: this.editingHeure,
      motif: appt.motif,
      statut: appt.statut,
      notes: appt.notes ?? ''
    };
    this.selectedPatientLabel.set(`${appt.patientNom} ${appt.patientPrenom}`);
    this.formSubmitted.set(false);
    this.loadCreneaux();
    this.openAppointmentModal();
  }

  private openAppointmentModal(): void {
    const modalRef = this.modalService.open(this.appointmentModal, {
      centered: true,
      size: 'lg',
      beforeDismiss: () =>
        confirmDiscardIfDirty(this.appointmentFormRef ?? { dirty: false }, this.translocoService.translate('common.modal.confirmDiscard'))
    });
    focusFirstField(modalRef, this.modalMode === 'create' ? 'appointmentPatient' : 'appointmentDate');
  }

  // Charge les créneaux libres pour le couple médecin/date. Tant qu'il en manque un (un ADMIN doit
  private loadCreneaux(): void {
    const { date, medecinId } = this.appointmentModel;
    if (!date || medecinId == null) {
      this.creneaux.set([]);
      this.creneauxError.set(false);
      return;
    }

    this.creneauxLoading.set(true);
    this.creneauxError.set(false);
    this.appointmentService.getDisponibilites(date, medecinId).subscribe({
      next: (libres) => {
        this.creneaux.set(this.withEditedCreneau(libres, date));
        this.creneauxLoading.set(false);
        if (this.appointmentModel.heure && !this.creneaux().includes(this.appointmentModel.heure)) {
          this.appointmentModel.heure = '';
        }
      },
      error: () => {
        this.creneaux.set([]);
        this.creneauxLoading.set(false);
        this.creneauxError.set(true);
      }
    });
  }

  // Réinjecte l'heure du rendez-vous édité : le backend la voit occupée par lui-même, et elle peut
  private withEditedCreneau(libres: string[], date: string): string[] {
    if (this.modalMode !== 'edit' || date !== this.editingDate || libres.includes(this.editingHeure)) {
      return libres;
    }
    return [...libres, this.editingHeure].sort();
  }

  onFormMedecinChange(medecinId: number | null): void {
    this.appointmentModel.medecinId = medecinId;
    this.appointmentModel.heure = '';
    this.loadCreneaux();
  }

  onFormDateChange(date: string): void {
    this.appointmentModel.date = date;
    this.appointmentModel.heure = '';
    this.loadCreneaux();
  }

  readonly isAppointmentDateDisabled = (date: NgbDateStruct): boolean => {
    const isoDate = `${date.year}-${String(date.month).padStart(2, '0')}-${String(date.day).padStart(2, '0')}`;
    if (this.modalMode === 'edit' && isoDate === this.editingDate) {
      return false;
    }
    return new Date(Date.UTC(date.year, date.month - 1, date.day)).getUTCDay() === 0;
  };

  selectCreneau(heure: string): void {
    this.appointmentModel.heure = heure;
  }

  onSubmit(form: NgForm, modal: { close: () => void }): void {
    this.formSubmitted.set(true);

    if (form.invalid || this.appointmentModel.patientId == null || !this.appointmentModel.heure) {
      focusFirstInvalidField();
      return;
    }

    const commonRequest = {
      medecinId: this.appointmentModel.medecinId,
      dateHeure: `${this.appointmentModel.date}T${this.appointmentModel.heure}:00`,
      motif: this.appointmentModel.motif.trim(),
      notes: this.appointmentModel.notes?.trim() || null
    };

    this.formSubmitting.set(true);

    const isCreate = this.modalMode === 'create';
    const request$ = isCreate
      ? this.appointmentService.createAppointment({
          ...commonRequest,
          patientId: this.appointmentModel.patientId
        } satisfies CreateAppointmentRequest)
      : this.appointmentService.updateAppointment(this.editingId!, {
          ...commonRequest,
          statut: this.appointmentModel.statut
        } satisfies UpdateAppointmentRequest);

    request$.subscribe({
      next: () => {
        this.formSubmitting.set(false);
        modal.close();
        this.notificationService.showSuccess(
          this.translocoService.translate(isCreate ? 'appointment.success.created' : 'appointment.success.updated')
        );
        this.loadAppointments();
        this.calendarComponent?.loadMonth();
      },
      error: (err) => {
        this.formSubmitting.set(false);
        // Priorité au message backend (ex. "Le médecin a déjà un rendez-vous à 09:00") sur un code générique.
        this.notificationService.showError(
          extractBackendErrorMessage(err) ??
            this.translocoService.translate(
              err.status === 403
                ? 'appointment.errors.actionForbidden'
                : err.status === 409
                  ? 'appointment.errors.slotTaken'
                  : err.status === 400
                    ? 'appointment.errors.invalidData'
                    : err.status === 404
                      ? 'appointment.errors.notFound'
                      : isCreate
                        ? 'appointment.errors.createFailed'
                        : 'appointment.errors.updateFailed'
            )
        );
        // 409 : créneau pris entre-temps par un autre utilisateur, on rafraîchit la liste sans fermer le modal.
        if (err.status === 409) {
          this.loadCreneaux();
        }
      }
    });
  }

  onDelete(appt: Appointment): void {
    this.pendingAppointment = appt;
    this.modalService.open(this.confirmDeleteModal, { centered: true, backdrop: 'static' }).result.then(
      (result) => {
        if (result !== 'confirm') {
          return;
        }

        this.appointmentService.deleteAppointment(appt.id).subscribe({
          next: () => {
            this.notificationService.showSuccess(this.translocoService.translate('appointment.success.deleted'));
            this.loadAppointments();
            this.calendarComponent?.loadMonth();
          },
          error: (err) => {
            this.notificationService.showError(
              this.translocoService.translate(err.status === 403 ? 'appointment.errors.deleteForbidden' : 'appointment.errors.deleteFailed')
            );
          }
        });
      },
      () => {}
    );
  }

  onDayClick(dateKey: string): void {
    this.openCreateModal(dateKey);
  }

  onCalendarDayAppointmentsClick(dateKey: string): void {
    this.searchInput.set('');
    this.searchTerm.set('');
    this.statutFilter.set('');
    this.medecinIdFilter.set(null);
    this.dateFromFilter.set(dateKey);
    this.dateToFilter.set(dateKey);
    this.filtersPanelOpen.set(true);
    this.viewMode.set('list');
    this.resetToFirstPage();
    this.syncFiltersToUrl();
  }

  onAppointmentClick(appt: Appointment): void {
    this.openEditModal(appt);
  }

  /** "Démarrer la consultation" : uniquement pour un rendez-vous du jour pas encore honoré/annulé. */
  canStartConsultation(appt: Appointment): boolean {
    return appt.dateHeure.slice(0, 10) === this.todayLocalDate && (appt.statut === 'PLANIFIE' || appt.statut === 'CONFIRME');
  }

  readonly canStartConsultationInTable = (appointment: Appointment): boolean => this.canStartConsultation(appointment);

  onTableStatusChange(change: AppointmentStatusChange): void {
    this.quickStatus(change.appointment, change.statut);
  }

  onStartConsultation(appt: Appointment): void {
    this.router.navigate(['/patients', appt.patientId], {
      queryParams: { openConsultation: 1, appointmentId: appt.id, motif: appt.motif, date: appt.dateHeure.slice(0, 10) }
    });
  }

  onViewConsultation(appt: Appointment): void {
    this.consultationLookupId.set(appt.id);
    this.medicalRecordService.getConsultationByAppointment(appt.id).subscribe({
      next: (result) => {
        this.consultationLookupId.set(null);
        if (result.exists && result.consultationId != null) {
          this.router.navigate(['/patients', appt.patientId], {
            queryParams: { consultationId: result.consultationId }
          });
        } else {
          this.consultationUnavailableIds.update((ids) => new Set(ids).add(appt.id));
          this.notificationService.showWarning(this.translocoService.translate('appointment.list.quickActions.noConsultation'));
        }
      },
      error: () => {
        this.consultationLookupId.set(null);
        this.notificationService.showError(this.translocoService.translate('appointment.errors.loadFailed'));
      }
    });
  }

  // Change juste le statut sans ouvrir le modal complet ; on renvoie l'objet entier car le backend
  quickStatus(appt: Appointment, statut: AppointmentStatut): void {
    if (appt.statut === statut) {
      return;
    }
    this.statusUpdatingId.set(appt.id);
    const request: UpdateAppointmentRequest = {
      medecinId: appt.medecinId,
      dateHeure: appt.dateHeure,
      motif: appt.motif,
      statut,
      notes: appt.notes
    };
    this.appointmentService.updateAppointment(appt.id, request).subscribe({
      next: () => {
        this.statusUpdatingId.set(null);
        this.notificationService.showSuccess(this.translocoService.translate('appointment.success.updated'));
        this.loadAppointments();
        this.calendarComponent?.loadMonth();
      },
      error: (err) => {
        this.statusUpdatingId.set(null);
        this.notificationService.showError(
          extractBackendErrorMessage(err) ??
            this.translocoService.translate(err.status === 403 ? 'appointment.errors.actionForbidden' : 'appointment.errors.updateFailed')
        );
      }
    });
  }
}
