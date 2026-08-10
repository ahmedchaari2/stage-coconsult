import { Component, OnInit, TemplateRef, ViewChild, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { NgbModal, NgbModalModule, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import {
  MedicineBoxOutline,
  EditOutline,
  EyeOutline,
  InboxOutline,
  RollbackOutline,
  PrinterOutline,
  PlusOutline,
  RedoOutline,
  CheckCircleOutline,
  ClockCircleOutline
} from '@ant-design/icons-angular/icons';
import { CardComponent } from 'src/app/theme/shared/components/card/card.component';
import { Pagination } from 'src/app/theme/shared/components/pagination/pagination';
import { DebounceInput } from 'src/app/theme/shared/directives/debounce-input';
import { AdvancedFilters, FilterChip } from 'src/app/theme/shared/components/advanced-filters/advanced-filters';
import { DateInputComponent } from 'src/app/theme/shared/components/date-input/date-input';
import { isDateRangeInvalid } from 'src/app/core/utils/date-range';
import { isMobileViewport } from 'src/app/core/utils/viewport';
import { PrescriptionService } from 'src/app/core/services/prescription.service';
import { PatientService } from 'src/app/core/services/patient.service';
import { MedecinService } from 'src/app/core/services/medecin.service';
import { AuthService } from 'src/app/core/services/auth.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { downloadBlob } from 'src/app/core/utils/file-download';
import { formatDateDisplay } from 'src/app/core/utils/date-format';
import { DateFormatPipe } from 'src/app/core/pipes/date-format.pipe';
import { prescriptionStatutCalcule } from 'src/app/core/utils/prescription-status';
import { Prescription } from 'src/app/core/models/prescription.model';
import { PatientOption } from 'src/app/core/models/appointment.model';
import { MedecinOption } from 'src/app/core/models/medecin.model';
import { PrescriptionEditModalComponent } from '../edit-modal/prescription-edit-modal.component';
import { PrescriptionCreateWizardComponent } from '../create-wizard/prescription-create-wizard.component';
import { SortableColumn, SortState } from 'src/app/theme/shared/directives/sortable-column';
import { SpinnerComponent } from 'src/app/theme/shared/components/spinner/spinner.component';
import { Spinkit } from 'src/app/theme/shared/components/spinner/spinkits';

@Component({
  selector: 'app-prescription-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CardComponent,
    Pagination,
    DebounceInput,
    AdvancedFilters,
    DateInputComponent,
    NgbModalModule,
    NgbTooltip,
    TranslocoModule,
    RouterLink,
    IconDirective,
    PrescriptionEditModalComponent,
    PrescriptionCreateWizardComponent,
    DateFormatPipe,
    SortableColumn,
    SpinnerComponent
  ],
  templateUrl: './prescription-list.component.html',
  styleUrl: './prescription-list.component.scss'
})
export class PrescriptionListComponent implements OnInit {
  private readonly prescriptionService = inject(PrescriptionService);
  private readonly patientService = inject(PatientService);
  private readonly medecinService = inject(MedecinService);
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly translocoService = inject(TranslocoService);
  private readonly iconService = inject(IconService);
  private readonly modalService = inject(NgbModal);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  @ViewChild('confirmArchiveModal') confirmArchiveModal!: TemplateRef<unknown>;
  @ViewChild('prescriptionViewModal') prescriptionViewModal!: TemplateRef<unknown>;
  @ViewChild(PrescriptionEditModalComponent) editModalCmp!: PrescriptionEditModalComponent;
  @ViewChild(PrescriptionCreateWizardComponent) createWizardCmp!: PrescriptionCreateWizardComponent;

  pendingPrescription: Prescription | null = null;
  pendingAction: 'archive' | 'restore' = 'archive';
  viewedPrescription: Prescription | null = null;
  viewLoadingId = signal<number | null>(null);

  ordonnanceLoadingId = signal<number | null>(null);

  private readonly currentUser = this.authService.currentUser;
  readonly isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');

  prescriptions = signal<Prescription[]>([]);
  isLoading = signal(true);
  error = signal<string | null>(null);
  page = signal(0);
  size = signal(10);
  totalPages = signal(0);
  totalElements = signal(0);
  /** null = tri par défaut du backend (createdAt desc), pas d'en-tête surligné. */
  sortState = signal<SortState | null>(null);

  searchInput = signal('');
  searchTerm = signal('');

  patientIdFilter = signal<number | null>(null);
  medecinIdFilter = signal<number | null>(null);
  dateFromFilter = signal('');
  dateToFilter = signal('');
  readonly dateRangeInvalid = computed(() => isDateRangeInvalid(this.dateFromFilter(), this.dateToFilter()));
  showArchivedFilter = signal(false);
  statutCalculeFilter = signal<'' | 'ACTIVE' | 'EXPIREE'>('');

  filtersPanelOpen = signal(false);

  patientFilterSearchInput = signal('');
  patientFilterResults = signal<PatientOption[]>([]);
  patientFilterLoading = signal(false);
  selectedPatientFilterLabel = signal('');

  medecins = signal<MedecinOption[]>([]);

  readonly activeFilterCount = computed(() => {
    let count = 0;
    if (this.patientIdFilter() != null) count++;
    if (this.medecinIdFilter() != null) count++;
    if (this.dateFromFilter()) count++;
    if (this.dateToFilter()) count++;
    if (this.showArchivedFilter()) count++;
    if (this.statutCalculeFilter()) count++;
    return count;
  });

  readonly filterChips = computed<FilterChip[]>(() => {
    const chips: FilterChip[] = [];
    if (this.patientIdFilter() != null) {
      chips.push({
        key: 'patient',
        label: this.translocoService.translate('prescription.list.filters.chips.patient'),
        value: this.selectedPatientFilterLabel()
      });
    }
    const medecinId = this.medecinIdFilter();
    if (medecinId != null) {
      const m = this.medecins().find((x) => x.id === medecinId);
      chips.push({
        key: 'medecin',
        label: this.translocoService.translate('prescription.list.filters.chips.medecin'),
        value: m ? `${m.nom} ${m.prenom}` : `#${medecinId}`
      });
    }
    if (this.dateFromFilter())
      chips.push({
        key: 'dateFrom',
        label: this.translocoService.translate('prescription.list.filters.chips.dateFrom'),
        value: formatDateDisplay(this.dateFromFilter())
      });
    if (this.dateToFilter())
      chips.push({
        key: 'dateTo',
        label: this.translocoService.translate('prescription.list.filters.chips.dateTo'),
        value: formatDateDisplay(this.dateToFilter())
      });
    if (this.showArchivedFilter())
      chips.push({
        key: 'archived',
        label: this.translocoService.translate('prescription.list.filters.chips.archived'),
        value: this.translocoService.translate('prescription.list.filters.chips.archivedValue')
      });
    if (this.statutCalculeFilter()) {
      const statutKey = this.statutCalculeFilter() === 'ACTIVE' ? 'prescription.list.statut.active' : 'prescription.list.statut.expiree';
      chips.push({
        key: 'statutCalcule',
        label: this.translocoService.translate('prescription.list.filters.chips.statutCalcule'),
        value: this.translocoService.translate(statutKey)
      });
    }
    return chips;
  });

  constructor() {
    this.iconService.addIcon(
      MedicineBoxOutline,
      EditOutline,
      EyeOutline,
      InboxOutline,
      RollbackOutline,
      PrinterOutline,
      PlusOutline,
      RedoOutline,
      CheckCircleOutline,
      ClockCircleOutline
    );
  }

  readonly statutCalcule = prescriptionStatutCalcule;
  readonly spinkit = Spinkit.skLine;

  ngOnInit(): void {
    if (this.isAdmin()) {
      this.medecinService.getAllMedecinOptions().subscribe({
        next: (options) => this.medecins.set(options),
        error: () => {}
      });
    }

    const params = this.route.snapshot.queryParamMap;
    if (params.get('medecinId')) this.medecinIdFilter.set(Number(params.get('medecinId')));
    if (params.get('dateFrom')) this.dateFromFilter.set(params.get('dateFrom')!);
    if (params.get('dateTo')) this.dateToFilter.set(params.get('dateTo')!);
    if (params.get('archived') === 'true') this.showArchivedFilter.set(true);
    if (params.get('statutCalcule')) this.statutCalculeFilter.set(params.get('statutCalcule') as 'ACTIVE' | 'EXPIREE');
    if (params.get('sort') && (params.get('direction') === 'asc' || params.get('direction') === 'desc')) {
      this.sortState.set({ field: params.get('sort')!, direction: params.get('direction') as 'asc' | 'desc' });
    }
    const patientId = params.get('patientId');
    if (patientId) {
      this.patientIdFilter.set(Number(patientId));
      this.patientService.getPatientById(Number(patientId)).subscribe({
        next: (p) => {
          this.selectedPatientFilterLabel.set(`${p.nom} ${p.prenom}`);
          this.patientFilterSearchInput.set(`${p.nom} ${p.prenom}`);
        },
        error: () => {}
      });
    }
    if (this.activeFilterCount() > 0) this.filtersPanelOpen.set(true);

    this.loadPrescriptions();
  }

  private syncFiltersToUrl(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        patientId: this.patientIdFilter(),
        medecinId: this.medecinIdFilter(),
        dateFrom: this.dateFromFilter() || null,
        dateTo: this.dateToFilter() || null,
        archived: this.showArchivedFilter() || null,
        statutCalcule: this.statutCalculeFilter() || null,
        sort: this.sortState()?.field || null,
        direction: this.sortState()?.direction || null
      },
      replaceUrl: true
    });
  }

  loadPrescriptions(): void {
    this.isLoading.set(true);
    this.error.set(null);
    this.syncFiltersToUrl();

    this.prescriptionService
      .search(this.page(), this.size(), {
        q: this.searchTerm() || undefined,
        patientId: this.patientIdFilter() ?? undefined,
        medecinId: this.medecinIdFilter() ?? undefined,
        dateFrom: this.dateFromFilter() || undefined,
        dateTo: this.dateToFilter() && !this.dateRangeInvalid() ? this.dateToFilter() : undefined,
        archived: this.showArchivedFilter(),
        statutCalcule: this.statutCalculeFilter() || undefined,
        sort: this.sortState()?.field,
        direction: this.sortState()?.direction
      })
      .subscribe({
        next: (data) => {
          this.prescriptions.set(data.content);
          this.totalPages.set(data.totalPages);
          this.totalElements.set(data.totalElements);
          this.isLoading.set(false);
        },
        error: (err) => {
          this.error.set(
            extractBackendErrorMessage(err) ??
              this.translocoService.translate(
                err.status === 401
                  ? 'prescription.list.errors.unauthorized'
                  : err.status === 403
                    ? 'prescription.list.errors.forbidden'
                    : 'prescription.list.errors.loadFailed'
              )
          );
          this.isLoading.set(false);
        }
      });
  }

  onPageChange(newPage: number): void {
    this.page.set(newPage);
    this.loadPrescriptions();
  }

  onSizeChange(newSize: number): void {
    this.size.set(newSize);
    this.page.set(0);
    this.loadPrescriptions();
  }

  private resetToFirstPage(): void {
    this.page.set(0);
    this.loadPrescriptions();
  }

  onSearchChange(term: string): void {
    this.searchTerm.set(term);
    this.resetToFirstPage();
  }

  onSortChange(sort: SortState): void {
    this.sortState.set(sort);
    this.resetToFirstPage();
  }

  private applyPanelFilters(): void {
    if (isMobileViewport()) return;
    this.resetToFirstPage();
  }

  onApplyPanelFilters(): void {
    this.resetToFirstPage();
  }

  onPatientFilterSearchChange(term: string): void {
    if (this.patientIdFilter() != null) {
      this.patientIdFilter.set(null);
      this.selectedPatientFilterLabel.set('');
      this.applyPanelFilters();
    }

    if (!term.trim()) {
      this.patientFilterResults.set([]);
      return;
    }

    this.patientFilterLoading.set(true);
    this.patientService.getPatients(0, 8, { q: term }).subscribe({
      next: (data) => {
        this.patientFilterResults.set(data.content.map((p) => ({ id: p.id, nom: p.nom, prenom: p.prenom })));
        this.patientFilterLoading.set(false);
      },
      error: () => {
        this.patientFilterResults.set([]);
        this.patientFilterLoading.set(false);
      }
    });
  }

  onPatientFilterBlur(): void {
    setTimeout(() => this.patientFilterResults.set([]), 150);
  }

  selectPatientFilter(p: PatientOption): void {
    this.patientIdFilter.set(p.id);
    this.selectedPatientFilterLabel.set(`${p.nom} ${p.prenom}`);
    this.patientFilterSearchInput.set(`${p.nom} ${p.prenom}`);
    this.patientFilterResults.set([]);
    this.applyPanelFilters();
  }

  onMedecinFilterChange(value: number | null): void {
    this.medecinIdFilter.set(value);
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

  onShowArchivedFilterChange(value: boolean): void {
    this.showArchivedFilter.set(value);
    this.applyPanelFilters();
  }

  onFilterChipRemove(key: string): void {
    switch (key) {
      case 'patient':
        this.patientIdFilter.set(null);
        this.selectedPatientFilterLabel.set('');
        this.patientFilterSearchInput.set('');
        break;
      case 'medecin':
        this.medecinIdFilter.set(null);
        break;
      case 'dateFrom':
        this.dateFromFilter.set('');
        break;
      case 'dateTo':
        this.dateToFilter.set('');
        break;
      case 'archived':
        this.showArchivedFilter.set(false);
        break;
      case 'statutCalcule':
        this.statutCalculeFilter.set('');
        break;
    }
    this.resetToFirstPage();
  }

  onStatutCalculeFilterChange(value: '' | 'ACTIVE' | 'EXPIREE'): void {
    this.statutCalculeFilter.set(value);
    this.applyPanelFilters();
  }

  onResetAllFilters(): void {
    this.searchInput.set('');
    this.searchTerm.set('');
    this.patientIdFilter.set(null);
    this.selectedPatientFilterLabel.set('');
    this.patientFilterSearchInput.set('');
    this.medecinIdFilter.set(null);
    this.dateFromFilter.set('');
    this.dateToFilter.set('');
    this.showArchivedFilter.set(false);
    this.statutCalculeFilter.set('');
    this.resetToFirstPage();
  }

  onView(p: Prescription): void {
    this.viewLoadingId.set(p.id);
    this.prescriptionService.getById(p.id).subscribe({
      next: (prescription) => {
        this.viewLoadingId.set(null);
        this.viewedPrescription = prescription;
        this.modalService.open(this.prescriptionViewModal, { centered: true, size: 'lg' });
      },
      error: (err) => {
        this.viewLoadingId.set(null);
        this.notificationService.showError(
          extractBackendErrorMessage(err) ?? this.translocoService.translate('prescription.errors.loadFailed')
        );
      }
    });
  }

  onEdit(p: Prescription): void {
    this.editModalCmp.open(p);
  }

  onPrescriptionSaved(): void {
    this.loadPrescriptions();
  }

  openCreateWizard(): void {
    this.createWizardCmp.openCreate();
  }

  onRenew(p: Prescription): void {
    this.createWizardCmp.openRenewal(p);
  }

  onWizardSaved(): void {
    this.loadPrescriptions();
  }

  onPrintOrdonnance(p: Prescription): void {
    if (p.archived) {
      return;
    }

    this.ordonnanceLoadingId.set(p.id);
    this.prescriptionService.downloadOrdonnance(p.consultationId).subscribe({
      next: (blob) => {
        this.ordonnanceLoadingId.set(null);
        downloadBlob(blob, `ordonnance-${p.consultationId}.pdf`);
      },
      error: (err) => {
        this.ordonnanceLoadingId.set(null);
        this.notificationService.showError(
          extractBackendErrorMessage(err) ??
            this.translocoService.translate(
              err.status === 403 ? 'prescription.errors.actionForbidden' : 'prescription.errors.ordonnanceFailed'
            )
        );
      }
    });
  }

  onArchiveToggle(p: Prescription): void {
    this.pendingPrescription = p;
    this.pendingAction = p.archived ? 'restore' : 'archive';
    this.modalService.open(this.confirmArchiveModal, { centered: true, backdrop: 'static' }).result.then(
      (result) => {
        if (result !== 'confirm') {
          return;
        }

        const request$: Observable<unknown> = p.archived ? this.prescriptionService.restore(p.id) : this.prescriptionService.archive(p.id);

        request$.subscribe({
          next: () => {
            this.notificationService.showSuccess(
              this.translocoService.translate(p.archived ? 'prescription.success.restored' : 'prescription.success.archived')
            );
            this.loadPrescriptions();
          },
          error: (err) => {
            const forbiddenKey =
              this.pendingAction === 'restore' ? 'prescription.errors.restoreForbidden' : 'prescription.errors.archiveForbidden';
            const failedKey = this.pendingAction === 'restore' ? 'prescription.errors.restoreFailed' : 'prescription.errors.archiveFailed';
            this.notificationService.showError(
              extractBackendErrorMessage(err) ?? this.translocoService.translate(err.status === 403 ? forbiddenKey : failedKey)
            );
          }
        });
      },
      () => {}
    );
  }
}
