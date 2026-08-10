import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import { AuditOutline } from '@ant-design/icons-angular/icons';
import { CardComponent } from 'src/app/theme/shared/components/card/card.component';
import { Pagination } from 'src/app/theme/shared/components/pagination/pagination';
import { AdvancedFilters, FilterChip } from 'src/app/theme/shared/components/advanced-filters/advanced-filters';
import { DateInputComponent } from 'src/app/theme/shared/components/date-input/date-input';
import { AccesLogService, AccesLogFilterParams } from 'src/app/core/services/acces-log.service';
import { MedecinService } from 'src/app/core/services/medecin.service';
import { PatientService } from 'src/app/core/services/patient.service';
import { AuthService } from 'src/app/core/services/auth.service';
import { AccesLog, TypeAction, TypeRessource } from 'src/app/core/models/acces-log.model';
import { MedecinOption } from 'src/app/core/models/medecin.model';
import { PatientOption } from 'src/app/core/models/appointment.model';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { formatDateDisplay } from 'src/app/core/utils/date-format';
import { DateTimeFormatPipe } from 'src/app/core/pipes/date-time-format.pipe';
import { isDateRangeInvalid } from 'src/app/core/utils/date-range';
import { SortableColumn, SortState } from 'src/app/theme/shared/directives/sortable-column';
import { SpinnerComponent } from 'src/app/theme/shared/components/spinner/spinner.component';
import { Spinkit } from 'src/app/theme/shared/components/spinner/spinkits';

@Component({
  selector: 'app-acces-log-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CardComponent,
    Pagination,
    AdvancedFilters,
    DateInputComponent,
    TranslocoModule,
    IconDirective,
    DateTimeFormatPipe,
    SortableColumn,
    SpinnerComponent
  ],
  templateUrl: './acces-log-list.component.html',
  styleUrl: './acces-log-list.component.scss'
})
export class AccesLogListComponent implements OnInit {
  private readonly accesLogService = inject(AccesLogService);
  private readonly medecinService = inject(MedecinService);
  private readonly patientService = inject(PatientService);
  private readonly authService = inject(AuthService);
  private readonly translocoService = inject(TranslocoService);
  private readonly iconService = inject(IconService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly typeRessourceOptions: TypeRessource[] = ['PATIENT', 'MEDECIN', 'RENDEZ_VOUS', 'DOSSIER_MEDICAL', 'CONSULTATION', 'PRESCRIPTION'];
  readonly actionOptions: TypeAction[] = ['VIEW', 'CREATE', 'UPDATE', 'ARCHIVE', 'RESTORE', 'DELETE', 'PRINT', 'RENEW', 'STATUS_CHANGE'];

  readonly spinkit = Spinkit.skLine;

  constructor() {
    this.iconService.addIcon(AuditOutline);
  }

  accesLogs = signal<AccesLog[]>([]);
  isLoading = signal(true);
  error = signal<string | null>(null);
  page = signal(0);
  size = signal(10);
  totalPages = signal(0);
  totalElements = signal(0);
  /** null = tri par défaut du backend (dateHeure desc), pas d'en-tête surligné. */
  sortState = signal<SortState | null>(null);

  // Recherche backend : nom/prénom du patient ou de l'utilisateur concerné (voir AccesLogQueryService).
  searchInput = signal('');
  searchTerm = signal('');

  private medecinNames = signal(new Map<number, string>());
  private patientNames = signal(new Map<number, string>());
  medecins = signal<MedecinOption[]>([]);

  utilisateurIdFilter = signal<number | null>(null);
  typeRessourceFilter = signal<TypeRessource | ''>('');
  actionFilter = signal<TypeAction | ''>('');
  dateFromFilter = signal('');
  dateToFilter = signal('');
  readonly dateRangeInvalid = computed(() => isDateRangeInvalid(this.dateFromFilter(), this.dateToFilter()));

  patientIdFilter = signal<number | null>(null);
  patientLabel = signal('');
  patientSearchInput = signal('');
  patientSearchResults = signal<PatientOption[]>([]);
  patientSearchLoading = signal(false);

  readonly activeFilterCount = computed(() => {
    let count = 0;
    if (this.utilisateurIdFilter() != null) count++;
    if (this.patientIdFilter() != null) count++;
    if (this.typeRessourceFilter()) count++;
    if (this.actionFilter()) count++;
    if (this.dateFromFilter()) count++;
    if (this.dateToFilter()) count++;
    return count;
  });

  readonly filterChips = computed<FilterChip[]>(() => {
    const chips: FilterChip[] = [];
    const utilisateurId = this.utilisateurIdFilter();
    if (utilisateurId != null) {
      chips.push({
        key: 'utilisateur',
        label: this.translocoService.translate('accesLog.filters.chips.utilisateur'),
        value: this.medecinNames().get(utilisateurId) ?? `#${utilisateurId}`
      });
    }
    if (this.patientIdFilter() != null) {
      chips.push({ key: 'patient', label: this.translocoService.translate('accesLog.filters.chips.patient'), value: this.patientLabel() });
    }
    if (this.typeRessourceFilter()) {
      chips.push({
        key: 'typeRessource',
        label: this.translocoService.translate('accesLog.filters.chips.typeRessource'),
        value: this.translocoService.translate('accesLog.typeRessource.' + this.typeRessourceFilter())
      });
    }
    if (this.actionFilter()) {
      chips.push({
        key: 'action',
        label: this.translocoService.translate('accesLog.filters.chips.action'),
        value: this.translocoService.translate('accesLog.action.' + this.actionFilter())
      });
    }
    if (this.dateFromFilter()) {
      chips.push({
        key: 'dateFrom',
        label: this.translocoService.translate('common.filters.from'),
        value: formatDateDisplay(this.dateFromFilter())
      });
    }
    if (this.dateToFilter()) {
      chips.push({
        key: 'dateTo',
        label: this.translocoService.translate('common.filters.to'),
        value: formatDateDisplay(this.dateToFilter())
      });
    }
    return chips;
  });

  ngOnInit(): void {
    const currentUser = this.authService.currentUser();
    if (currentUser) {
      this.setMedecinName(currentUser.id, `${currentUser.nom} ${currentUser.prenom}`);
      this.medecins.update((options) => [{ id: currentUser.id, nom: currentUser.nom, prenom: currentUser.prenom }, ...options]);
    }

    this.medecinService.getAllMedecinOptions().subscribe({
      next: (options) => {
        const currentUserId = currentUser?.id;
        this.medecins.update((existing) => [...existing, ...options.filter((option) => option.id !== currentUserId)]);
        options.forEach((m) => this.setMedecinName(Number(m.id), `${m.nom} ${m.prenom}`));
      },
      error: () => {}
    });

    const params = this.route.snapshot.queryParamMap;
    const patientId = params.get('patientId');
    if (patientId) {
      this.patientIdFilter.set(+patientId);
      this.patientLabel.set(params.get('patientLabel') ?? `#${patientId}`);
      this.patientSearchInput.set(this.patientLabel());
      this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
    }

    if (params.get('sort') && (params.get('direction') === 'asc' || params.get('direction') === 'desc')) {
      this.sortState.set({ field: params.get('sort')!, direction: params.get('direction') as 'asc' | 'desc' });
    }

    this.loadAccesLogs();
  }

  loadAccesLogs(): void {
    this.isLoading.set(true);
    this.error.set(null);

    const filters: AccesLogFilterParams = {};
    if (this.searchTerm()) filters.q = this.searchTerm();
    if (this.utilisateurIdFilter() != null) filters.utilisateurId = this.utilisateurIdFilter()!;
    if (this.patientIdFilter() != null) filters.patientId = this.patientIdFilter()!;
    if (this.typeRessourceFilter()) filters.typeRessource = this.typeRessourceFilter() as TypeRessource;
    if (this.actionFilter()) filters.action = this.actionFilter() as TypeAction;
    if (this.dateFromFilter()) filters.dateFrom = `${this.dateFromFilter()}T00:00:00`;
    if (this.dateToFilter() && !this.dateRangeInvalid()) filters.dateTo = `${this.dateToFilter()}T23:59:59`;
    if (this.sortState()) {
      filters.sort = this.sortState()!.field;
      filters.direction = this.sortState()!.direction;
    }

    this.accesLogService.getAccesLogs(this.page(), this.size(), filters).subscribe({
      next: (data) => {
        this.accesLogs.set(data.content);
        this.totalPages.set(data.totalPages);
        this.totalElements.set(data.totalElements);
        this.isLoading.set(false);
        this.resolvePatientNames(data.content);
      },
      error: (err) => {
        this.error.set(
          extractBackendErrorMessage(err) ??
            this.translocoService.translate(err.status === 403 ? 'common.errors.adminRequired' : 'accesLog.errors.loadFailed')
        );
        this.isLoading.set(false);
      }
    });
  }

  private resolvePatientNames(entries: AccesLog[]): void {
    entries.forEach((entry) => {
      const embeddedName = [entry.patientNom, entry.patientPrenom].filter(Boolean).join(' ');
      if (embeddedName && entry.patientId !== null) this.setPatientName(entry.patientId, embeddedName);
    });

    const knownNames = this.patientNames();
    const missingIds = [
      ...new Set(
        entries
          .filter((entry) => entry.patientId !== null && !entry.patientNom && !entry.patientPrenom)
          .map((entry) => entry.patientId as number)
      )
    ].filter((id) => !knownNames.has(id));
    missingIds.forEach((id) => {
      this.patientService.getPatientById(id).subscribe({
        next: (p) => this.setPatientName(id, `${p.nom} ${p.prenom}`),
        error: () => {}
      });
    });
  }

  medecinDisplay(utilisateurId: number): string {
    return this.medecinNames().get(utilisateurId) ?? `#${utilisateurId}`;
  }

  resourceDisplay(entry: AccesLog): string {
    if (entry.typeRessource === 'MEDECIN') {
      const resourceName = [entry.ressourceNom, entry.ressourcePrenom].filter(Boolean).join(' ');
      return resourceName || `#${entry.ressourceId}`;
    }
    if (entry.patientId === null) return `#${entry.ressourceId}`;
    const patientName = [entry.patientNom, entry.patientPrenom].filter(Boolean).join(' ');
    return patientName || (this.patientNames().get(entry.patientId) ?? `#${entry.patientId}`);
  }

  private setMedecinName(id: number, name: string): void {
    this.medecinNames.update((names) => new Map(names).set(id, name));
  }

  private setPatientName(id: number, name: string): void {
    this.patientNames.update((names) => new Map(names).set(id, name));
  }

  onPageChange(newPage: number): void {
    this.page.set(newPage);
    this.loadAccesLogs();
  }

  onSizeChange(newSize: number): void {
    this.size.set(newSize);
    this.page.set(0);
    this.loadAccesLogs();
  }

  private resetToFirstPage(): void {
    this.page.set(0);
    this.loadAccesLogs();
  }

  onSearchChange(term: string): void {
    this.searchTerm.set(term);
    this.resetToFirstPage();
  }

  onSortChange(sort: SortState): void {
    this.sortState.set(sort);
    this.resetToFirstPage();
  }

  onUtilisateurFilterChange(value: number | null): void {
    this.utilisateurIdFilter.set(value);
    this.resetToFirstPage();
  }

  onTypeRessourceFilterChange(value: string): void {
    this.typeRessourceFilter.set(value as TypeRessource | '');
    this.resetToFirstPage();
  }

  onActionFilterChange(value: string): void {
    this.actionFilter.set(value as TypeAction | '');
    this.resetToFirstPage();
  }

  onDateFromFilterChange(value: string): void {
    this.dateFromFilter.set(value);
    this.resetToFirstPage();
  }

  onDateToFilterChange(value: string): void {
    this.dateToFilter.set(value);
    this.resetToFirstPage();
  }

  onPatientSearchChange(term: string): void {
    this.patientIdFilter.set(null);
    this.patientLabel.set('');

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
    this.patientIdFilter.set(p.id);
    this.patientLabel.set(`${p.nom} ${p.prenom}`);
    this.patientSearchInput.set(`${p.nom} ${p.prenom}`);
    this.patientSearchResults.set([]);
    this.resetToFirstPage();
  }

  clearPatientFilter(): void {
    this.patientIdFilter.set(null);
    this.patientLabel.set('');
    this.patientSearchInput.set('');
    this.resetToFirstPage();
  }

  onFilterChipRemove(key: string): void {
    switch (key) {
      case 'utilisateur':
        this.utilisateurIdFilter.set(null);
        break;
      case 'patient':
        this.clearPatientFilter();
        return;
      case 'typeRessource':
        this.typeRessourceFilter.set('');
        break;
      case 'action':
        this.actionFilter.set('');
        break;
      case 'dateFrom':
        this.dateFromFilter.set('');
        break;
      case 'dateTo':
        this.dateToFilter.set('');
        break;
    }
    this.resetToFirstPage();
  }

  onResetAllFilters(): void {
    this.searchInput.set('');
    this.searchTerm.set('');
    this.utilisateurIdFilter.set(null);
    this.typeRessourceFilter.set('');
    this.actionFilter.set('');
    this.dateFromFilter.set('');
    this.dateToFilter.set('');
    this.clearPatientFilter();
  }
}
