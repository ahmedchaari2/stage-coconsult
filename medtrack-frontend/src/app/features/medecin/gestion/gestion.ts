import { Component, computed, inject, OnInit, signal, TemplateRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { IconDirective } from '@ant-design/icons-angular';
import { NgbModal, NgbModalModule, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { CardComponent } from 'src/app/theme/shared/components/card/card.component';
import { SpinnerComponent } from 'src/app/theme/shared/components/spinner/spinner.component';
import { Spinkit } from 'src/app/theme/shared/components/spinner/spinkits';
import { Pagination } from 'src/app/theme/shared/components/pagination/pagination';
import { DebounceInput } from 'src/app/theme/shared/directives/debounce-input';
import { AdvancedFilters, FilterChip } from 'src/app/theme/shared/components/advanced-filters/advanced-filters';
import { MedecinService, MedecinFilterParams } from 'src/app/core/services/medecin.service';
import { InvitationService } from 'src/app/core/services/invitation.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { Medecin } from 'src/app/core/models/medecin.model';
import { InvitationSummary } from 'src/app/core/models/invitation.model';
import { MedecinEditModalComponent } from 'src/app/features/medecin/edit-modal/medecin-edit-modal';
import { MedecinStatutBadgeComponent } from 'src/app/theme/shared/components/medecin-statut-badge/medecin-statut-badge';
import { EmailLinkComponent } from 'src/app/theme/shared/components/email-link/email-link';
import { DateTimeFormatPipe } from 'src/app/core/pipes/date-time-format.pipe';
import { SortableColumn, SortState } from 'src/app/theme/shared/directives/sortable-column';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { isMobileViewport } from 'src/app/core/utils/viewport';
import { confirmDiscardIfDirty, focusFirstField, focusFirstInvalidField } from 'src/app/core/utils/modal-ux';
import { IconService } from '@ant-design/icons-angular';
import {
  UserAddOutline,
  ReloadOutline,
  TeamOutline,
  EyeOutline,
  EditOutline,
  StopOutline,
  CheckOutline,
  SearchOutline,
  WarningOutline,
  CloseCircleOutline,
  ClockCircleOutline
} from '@ant-design/icons-angular/icons';

@Component({
  selector: 'app-gestion',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    CardComponent,
    SpinnerComponent,
    Pagination,
    DebounceInput,
    AdvancedFilters,
    IconDirective,
    NgbModalModule,
    NgbTooltip,
    TranslocoModule,
    MedecinEditModalComponent,
    MedecinStatutBadgeComponent,
    EmailLinkComponent,
    DateTimeFormatPipe,
    SortableColumn
  ],
  templateUrl: './gestion.html',
  styleUrl: './gestion.scss'
})
export class Gestion implements OnInit {
  private readonly medecinService = inject(MedecinService);
  private readonly invitationService = inject(InvitationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly iconService = inject(IconService);
  private readonly modalService = inject(NgbModal);
  private readonly notificationService = inject(NotificationService);
  private readonly translocoService = inject(TranslocoService);

  pendingMedecin: Medecin | null = null;
  pendingAction = '';

  pendingInvitation: InvitationSummary | null = null;
  revokingId = signal<number | null>(null);

  get pendingActionKey(): string {
    return this.pendingAction === 'réactiver' ? 'medecin.actions.reactivate' : 'medecin.actions.deactivate';
  }

  @ViewChild('confirmModal') confirmModal!: TemplateRef<unknown>;
  @ViewChild('inviteModal') inviteModal!: TemplateRef<unknown>;
  @ViewChild('invitationForm') private invitationFormRef?: NgForm;
  @ViewChild('confirmRevokeModal') confirmRevokeModal!: TemplateRef<unknown>;

  @ViewChild(MedecinEditModalComponent) private editModalCmp!: MedecinEditModalComponent;

  spinkit = Spinkit.skLine;

  medecins = signal<Medecin[]>([]);
  isLoading = signal(true);
  listError = signal<string | null>(null);

  page = signal(0);
  size = signal(10);
  totalPages = signal(0);
  totalElements = signal(0);

  /** null = tri par défaut du backend (createdAt desc), pas d'en-tête surligné. */
  sortState = signal<SortState | null>(null);

  searchInput = signal('');
  searchTerm = signal('');

  nomInput = signal('');
  nomFilter = signal('');
  prenomInput = signal('');
  prenomFilter = signal('');
  specialiteInput = signal('');
  specialiteFilter = signal('');
  numeroOrdreInput = signal('');
  numeroOrdreFilter = signal('');
  actifFilter = signal<'' | 'true' | 'false'>('');

  sansSpecialiteFilter = signal(false);
  sansNumeroOrdreFilter = signal(false);

  filtersPanelOpen = signal(false);

  readonly activeFilterCount = computed(() => {
    let count = 0;
    if (this.nomFilter()) count++;
    if (this.prenomFilter()) count++;
    if (this.specialiteFilter()) count++;
    if (this.numeroOrdreFilter()) count++;
    if (this.actifFilter()) count++;
    if (this.sansSpecialiteFilter()) count++;
    if (this.sansNumeroOrdreFilter()) count++;
    return count;
  });

  readonly filterChips = computed<FilterChip[]>(() => {
    const chips: FilterChip[] = [];
    if (this.nomFilter())
      chips.push({ key: 'nom', label: this.translocoService.translate('medecin.gestion.filters.chips.nom'), value: this.nomFilter() });
    if (this.prenomFilter())
      chips.push({
        key: 'prenom',
        label: this.translocoService.translate('medecin.gestion.filters.chips.prenom'),
        value: this.prenomFilter()
      });
    if (this.specialiteFilter())
      chips.push({
        key: 'specialite',
        label: this.translocoService.translate('medecin.gestion.filters.chips.specialite'),
        value: this.specialiteFilter()
      });
    if (this.numeroOrdreFilter())
      chips.push({
        key: 'numeroOrdre',
        label: this.translocoService.translate('medecin.gestion.filters.chips.numeroOrdre'),
        value: this.numeroOrdreFilter()
      });
    if (this.actifFilter()) {
      const statutKey = this.actifFilter() === 'true' ? 'medecin.gestion.status.actif' : 'medecin.gestion.status.inactif';
      chips.push({
        key: 'actif',
        label: this.translocoService.translate('medecin.gestion.filters.chips.statut'),
        value: this.translocoService.translate(statutKey)
      });
    }
    if (this.sansSpecialiteFilter()) {
      chips.push({
        key: 'sansSpecialite',
        label: this.translocoService.translate('medecin.gestion.filters.chips.sansSpecialite'),
        value: this.translocoService.translate('medecin.gestion.filters.chips.oui')
      });
    }
    if (this.sansNumeroOrdreFilter()) {
      chips.push({
        key: 'sansNumeroOrdre',
        label: this.translocoService.translate('medecin.gestion.filters.chips.sansNumeroOrdre'),
        value: this.translocoService.translate('medecin.gestion.filters.chips.oui')
      });
    }
    return chips;
  });

  email = '';
  invitationLoading = signal(false);
  invitationError = signal<string | null>(null);

  invitations = signal<InvitationSummary[]>([]);
  invitationsLoading = signal(true);
  invitationsError = signal<string | null>(null);
  activeSection = signal<'medecins' | 'invitations'>('medecins');
  readonly pendingInvitationCount = computed(() => this.invitations().filter((invitation) => invitation.status === 'PENDING').length);
  resendingEmail = signal<string | null>(null);

  constructor() {
    this.iconService.addIcon(
      ...[
        UserAddOutline,
        ReloadOutline,
        TeamOutline,
        EyeOutline,
        EditOutline,
        StopOutline,
        CheckOutline,
        SearchOutline,
        WarningOutline,
        CloseCircleOutline,
        ClockCircleOutline
      ]
    );
  }

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    if (params.get('section') === 'invitations') this.activeSection.set('invitations');
    if (params.get('nom')) {
      this.nomInput.set(params.get('nom')!);
      this.nomFilter.set(params.get('nom')!);
    }
    if (params.get('prenom')) {
      this.prenomInput.set(params.get('prenom')!);
      this.prenomFilter.set(params.get('prenom')!);
    }
    if (params.get('specialite')) {
      this.specialiteInput.set(params.get('specialite')!);
      this.specialiteFilter.set(params.get('specialite')!);
    }
    if (params.get('numeroOrdre')) {
      this.numeroOrdreInput.set(params.get('numeroOrdre')!);
      this.numeroOrdreFilter.set(params.get('numeroOrdre')!);
    }
    if (params.get('actif') === 'true' || params.get('actif') === 'false') {
      this.actifFilter.set(params.get('actif') as 'true' | 'false');
    }
    if (params.get('sansSpecialite') === 'true') this.sansSpecialiteFilter.set(true);
    if (params.get('sansNumeroOrdre') === 'true') this.sansNumeroOrdreFilter.set(true);
    if (params.get('sort') && (params.get('direction') === 'asc' || params.get('direction') === 'desc')) {
      this.sortState.set({ field: params.get('sort')!, direction: params.get('direction') as 'asc' | 'desc' });
    }
    if (this.activeFilterCount() > 0) this.filtersPanelOpen.set(true);
    this.loadMedecins();
    this.loadInvitations();
  }

  private syncFiltersToUrl(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        nom: this.nomFilter() || null,
        prenom: this.prenomFilter() || null,
        specialite: this.specialiteFilter() || null,
        numeroOrdre: this.numeroOrdreFilter() || null,
        actif: this.actifFilter() || null,
        sansSpecialite: this.sansSpecialiteFilter() || null,
        sansNumeroOrdre: this.sansNumeroOrdreFilter() || null,
        sort: this.sortState()?.field || null,
        direction: this.sortState()?.direction || null,
        section: this.activeSection() === 'invitations' ? 'invitations' : null
      },
      replaceUrl: true
    });
  }

  loadInvitations(): void {
    this.invitationsLoading.set(true);
    this.invitationsError.set(null);

    this.invitationService.getInvitations().subscribe({
      next: (data) => {
        this.invitations.set(data);
        this.invitationsLoading.set(false);
      },
      error: (err) => {
        this.invitationsError.set(
          extractBackendErrorMessage(err) ??
            this.translocoService.translate(err.status === 403 ? 'common.errors.adminRequired' : 'medecin.errors.loadInvitationsFailed')
        );
        this.invitationsLoading.set(false);
      }
    });
  }

  showSection(section: 'medecins' | 'invitations'): void {
    this.activeSection.set(section);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { section: section === 'invitations' ? 'invitations' : null },
      queryParamsHandling: 'merge'
    });
  }

  onResendInvitation(invitation: InvitationSummary): void {
    this.resendingEmail.set(invitation.email);

    this.invitationService.sendInvitation({ email: invitation.email }).subscribe({
      next: () => {
        this.resendingEmail.set(null);
        this.notificationService.showSuccess(this.translocoService.translate('medecin.errors.invitationSuccess'));
        this.loadInvitations();
      },
      error: (err) => {
        this.resendingEmail.set(null);
        this.notificationService.showError(
          extractBackendErrorMessage(err) ?? this.translocoService.translate('medecin.errors.invitationFailed')
        );
      }
    });
  }

  onRevokeInvitation(invitation: InvitationSummary): void {
    this.pendingInvitation = invitation;
    this.modalService.open(this.confirmRevokeModal, { centered: true, backdrop: 'static' }).result.then(
      (result) => {
        if (result !== 'confirm') {
          return;
        }
        this.revokingId.set(invitation.id);
        this.invitationService.revokeInvitation(invitation.id).subscribe({
          next: () => {
            this.revokingId.set(null);
            this.notificationService.showSuccess(this.translocoService.translate('invitation.errors.revokeSuccess'));
            this.loadInvitations();
          },
          error: (err) => {
            this.revokingId.set(null);
            this.notificationService.showError(
              extractBackendErrorMessage(err) ?? this.translocoService.translate('invitation.errors.revokeFailed')
            );
          }
        });
      },
      () => {}
    );
  }

  loadMedecins(): void {
    this.isLoading.set(true);
    this.listError.set(null);
    this.syncFiltersToUrl();

    const filters: MedecinFilterParams = {};
    if (this.searchTerm()) filters.q = this.searchTerm();
    if (this.nomFilter()) filters.nom = this.nomFilter();
    if (this.prenomFilter()) filters.prenom = this.prenomFilter();
    if (this.specialiteFilter()) filters.specialite = this.specialiteFilter();
    if (this.numeroOrdreFilter()) filters.numeroOrdre = this.numeroOrdreFilter();
    if (this.actifFilter()) filters.actif = this.actifFilter() === 'true';
    if (this.sansSpecialiteFilter()) filters.sansSpecialite = true;
    if (this.sansNumeroOrdreFilter()) filters.sansNumeroOrdre = true;
    if (this.sortState()) {
      filters.sort = this.sortState()!.field;
      filters.direction = this.sortState()!.direction;
    }

    this.medecinService.getMedecins(this.page(), this.size(), filters).subscribe({
      next: (data) => {
        this.medecins.set(data.content);
        this.totalPages.set(data.totalPages);
        this.totalElements.set(data.totalElements);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.listError.set(
          extractBackendErrorMessage(err) ??
            this.translocoService.translate(
              err.status === 401
                ? 'common.errors.unauthorized'
                : err.status === 403
                  ? 'common.errors.adminRequired'
                  : 'medecin.errors.loadListFailed'
            )
        );
        this.isLoading.set(false);
      }
    });
  }

  onPageChange(newPage: number): void {
    this.page.set(newPage);
    this.loadMedecins();
  }

  onSizeChange(newSize: number): void {
    this.size.set(newSize);
    this.page.set(0);
    this.loadMedecins();
  }

  resetToFirstPage(): void {
    this.page.set(0);
    this.loadMedecins();
  }

  onSearchChange(term: string): void {
    this.searchTerm.set(term);
    this.resetToFirstPage();
  }

  onResetSearch(): void {
    this.searchInput.set('');
    this.searchTerm.set('');
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

  onNomFilterChange(value: string): void {
    this.nomFilter.set(value);
    this.applyPanelFilters();
  }

  onPrenomFilterChange(value: string): void {
    this.prenomFilter.set(value);
    this.applyPanelFilters();
  }

  onSpecialiteFilterChange(value: string): void {
    this.specialiteFilter.set(value);
    this.applyPanelFilters();
  }

  onNumeroOrdreFilterChange(value: string): void {
    this.numeroOrdreFilter.set(value);
    this.applyPanelFilters();
  }

  onActifFilterChange(value: '' | 'true' | 'false'): void {
    this.actifFilter.set(value);
    this.applyPanelFilters();
  }

  onFilterChipRemove(key: string): void {
    switch (key) {
      case 'nom':
        this.nomInput.set('');
        this.nomFilter.set('');
        break;
      case 'prenom':
        this.prenomInput.set('');
        this.prenomFilter.set('');
        break;
      case 'specialite':
        this.specialiteInput.set('');
        this.specialiteFilter.set('');
        break;
      case 'numeroOrdre':
        this.numeroOrdreInput.set('');
        this.numeroOrdreFilter.set('');
        break;
      case 'actif':
        this.actifFilter.set('');
        break;
      case 'sansSpecialite':
        this.sansSpecialiteFilter.set(false);
        break;
      case 'sansNumeroOrdre':
        this.sansNumeroOrdreFilter.set(false);
        break;
    }
    this.resetToFirstPage();
  }

  onResetAllFilters(): void {
    this.searchInput.set('');
    this.searchTerm.set('');
    this.nomInput.set('');
    this.nomFilter.set('');
    this.prenomInput.set('');
    this.prenomFilter.set('');
    this.specialiteInput.set('');
    this.specialiteFilter.set('');
    this.numeroOrdreInput.set('');
    this.numeroOrdreFilter.set('');
    this.actifFilter.set('');
    this.sansSpecialiteFilter.set(false);
    this.sansNumeroOrdreFilter.set(false);
    this.resetToFirstPage();
  }

  openInviteModal(): void {
    this.email = '';
    this.invitationError.set(null);
    const modalRef = this.modalService.open(this.inviteModal, {
      centered: true,
      beforeDismiss: () =>
        confirmDiscardIfDirty(this.invitationFormRef ?? { dirty: false }, this.translocoService.translate('common.modal.confirmDiscard'))
    });
    focusFirstField(modalRef, 'email');
  }

  onSubmitInvitation(form: NgForm, modal: { close: () => void }): void {
    if (form.invalid) {
      focusFirstInvalidField();
      return;
    }

    this.invitationLoading.set(true);
    this.invitationError.set(null);

    this.invitationService.sendInvitation({ email: this.email }).subscribe({
      next: () => {
        this.invitationLoading.set(false);
        this.email = '';
        form.resetForm();
        modal.close();
        this.notificationService.showSuccess(this.translocoService.translate('medecin.errors.invitationSuccess'));
        this.loadMedecins();
        this.loadInvitations();
      },
      error: (err) => {
        this.invitationLoading.set(false);
        this.invitationError.set(extractBackendErrorMessage(err) ?? this.translocoService.translate('medecin.errors.invitationFailed'));
      }
    });
  }

  onEdit(medecin: Medecin): void {
    this.editModalCmp.open(medecin);
  }

  onMedecinSaved(): void {
    this.loadMedecins();
  }

  onToggle(medecin: Medecin): void {
    this.pendingMedecin = medecin;
    this.pendingAction = medecin.actif ? 'désactiver' : 'réactiver';
    this.modalService.open(this.confirmModal, { centered: true, backdrop: 'static' }).result.then(
      (result) => {
        if (result !== 'confirm') {
          return;
        }

        this.medecinService.toggleMedecinStatus(medecin.id).subscribe({
          next: () => {
            this.loadMedecins();
          },
          error: (err) => {
            this.notificationService.showError(
              extractBackendErrorMessage(err) ??
                this.translocoService.translate(
                  err.status === 403
                    ? 'medecin.errors.actionForbidden'
                    : err.status === 404
                      ? 'medecin.errors.notFound'
                      : 'medecin.errors.toggleFailed'
                )
            );
          }
        });
      },
      () => {}
    );
  }
}
