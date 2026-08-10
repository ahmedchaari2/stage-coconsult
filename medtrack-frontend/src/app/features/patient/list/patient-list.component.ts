import { Component, computed, inject, OnInit, signal, TemplateRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { IconDirective } from '@ant-design/icons-angular';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { CardComponent } from 'src/app/theme/shared/components/card/card.component';
import { DebounceInput } from 'src/app/theme/shared/directives/debounce-input';
import { AdvancedFilters, FilterChip } from 'src/app/theme/shared/components/advanced-filters/advanced-filters';
import { DateInputComponent } from 'src/app/theme/shared/components/date-input/date-input';
import { isDateRangeInvalid } from 'src/app/core/utils/date-range';
import { formatDateDisplay } from 'src/app/core/utils/date-format';
import { minBirthDateIso } from 'src/app/core/utils/date-bounds';
import { isMobileViewport } from 'src/app/core/utils/viewport';
import { PatientService, PatientFilterParams } from 'src/app/core/services/patient.service';
import { MedecinService } from 'src/app/core/services/medecin.service';
import { AuthService } from 'src/app/core/services/auth.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { Patient, PatientRequest, SexePatient } from 'src/app/core/models/patient.model';
import { MedecinOption } from 'src/app/core/models/medecin.model';
import { PatientEditModalComponent } from 'src/app/features/patient/edit-modal/patient-edit-modal';
import { PatientTableComponent } from 'src/app/features/patient/components/patient-table/patient-table.component';
import { SortState } from 'src/app/theme/shared/directives/sortable-column';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { confirmDiscardIfDirty, focusFirstField, focusFirstInvalidField } from 'src/app/core/utils/modal-ux';
import { IconService } from '@ant-design/icons-angular';
import { InboxOutline, PlusOutline, RollbackOutline, SearchOutline } from '@ant-design/icons-angular/icons';

@Component({
  selector: 'app-patient-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule,
    CardComponent,
    DebounceInput,
    AdvancedFilters,
    DateInputComponent,
    IconDirective,
    NgbModalModule,
    TranslocoModule,
    PatientEditModalComponent,
    PatientTableComponent
  ],
  templateUrl: './patient-list.component.html',
  styleUrl: './patient-list.component.scss'
})
export class PatientListComponent implements OnInit {
  private readonly patientService = inject(PatientService);
  private readonly medecinService = inject(MedecinService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly iconService = inject(IconService);
  private readonly modalService = inject(NgbModal);
  private readonly notificationService = inject(NotificationService);
  private readonly translocoService = inject(TranslocoService);
  private readonly fb = inject(FormBuilder);

  // --- Rôle courant (le select "Médecin référent" du panneau de filtres est ADMIN uniquement,
  // un MEDECIN ne voit de toute façon que ses propres patients référents côté backend) ---
  private readonly currentUser = this.authService.currentUser;
  readonly isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');
  medecins = signal<MedecinOption[]>([]);

  // Index id -> médecin pour résoudre le nom du référent dans la colonne dédiée (ADMIN, liste chargée).
  private readonly medecinsById = computed(() => {
    const map = new Map<number, MedecinOption>();
    for (const m of this.medecins()) map.set(Number(m.id), m);
    return map;
  });

  /**
   * Libellé du médecin référent affiché en colonne : nom résolu depuis la liste (ADMIN), sinon
   * le médecin connecté lui-même (un MEDECIN ne charge pas la liste mais est le référent de ses
   * patients), sinon un repli "#id". "Non assigné" si aucun référent.
   */
  medecinReferentLabel(patient: Patient): string {
    if (patient.medecinReferentId == null) {
      return this.translocoService.translate('common.labels.unassigned');
    }
    const m = this.medecinsById().get(Number(patient.medecinReferentId));
    if (m) return `${m.nom} ${m.prenom}`;
    const u = this.currentUser();
    if (u && Number(u.id) === Number(patient.medecinReferentId)) return `${u.nom} ${u.prenom}`;
    return `#${patient.medecinReferentId}`;
  }

  readonly patientMedecinReferentLabel = (patient: Patient): string => this.medecinReferentLabel(patient);

  // Patient en attente de confirmation d'archivage/restauration (utilisé par le modal NgbModal)
  pendingPatient: Patient | null = null;
  pendingAction: 'archive' | 'restore' = 'archive';

  @ViewChild('confirmModal') confirmModal!: TemplateRef<unknown>;

  @ViewChild('createModal') createModal!: TemplateRef<unknown>;

  @ViewChild(PatientEditModalComponent) private editModalCmp!: PatientEditModalComponent;

  // Valeurs exactes de l'enum backend SexePatient (EnumType.STRING, valeur envoyée telle quelle)
  readonly sexeOptions: SexePatient[] = ['HOMME', 'FEMME'];

  createForm: FormGroup = this.fb.group({
    nom: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    prenom: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    dateNaissance: ['', [Validators.required, this.dateNotFutureValidator()]],
    telephone: ['', [Validators.required, Validators.pattern(/^(?:\+216[ .-]?)?(?:\d[ .-]?){8}$/)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    adresse: ['', [Validators.required, Validators.maxLength(500)]],
    medecinReferentId: [null as number | null],
    sexe: [null as SexePatient | null],
    contactUrgenceNom: [''],
    contactUrgenceTelephone: ['', Validators.pattern(/^(?:\+216[ .-]?)?(?:\d[ .-]?){8}$/)],
    cin: [''],
    numeroAssurance: ['']
  });
  isCreateSubmitting = signal(false);

  patients = signal<Patient[]>([]);
  isLoading = signal(true);
  error = signal<string | null>(null);

  // État de pagination (page courante 0-based, alignée sur le contrat backend)
  page = signal(0);
  size = signal(10);
  totalPages = signal(0);
  totalElements = signal(0);

  /** null = tri par défaut du backend (createdAt desc), pas d'en-tête surligné. */
  sortState = signal<SortState | null>(null);

  // est la valeur réellement appliquée (mise à jour uniquement après le debounce de
  searchInput = signal('');
  searchTerm = signal('');

  nomInput = signal('');
  nomFilter = signal('');
  prenomInput = signal('');
  prenomFilter = signal('');
  numeroDossierInput = signal('');
  numeroDossierFilter = signal('');
  telephoneInput = signal('');
  telephoneFilter = signal('');
  dateNaissanceFromFilter = signal('');
  dateNaissanceToFilter = signal('');
  readonly dateNaissanceRangeInvalid = computed(() => isDateRangeInvalid(this.dateNaissanceFromFilter(), this.dateNaissanceToFilter()));
  medecinReferentIdFilter = signal<number | null>(null);
  /** Toggle "Afficher les archivés" du panneau (ADMIN uniquement, application immédiate). */
  includeArchivedFilter = signal(false);

  /**
   * Compteurs "qualité des données" du dashboard (ADMIN) : arrivée via un lien préfiltré
   * (GET /patients?sansNumeroDossier=true ou ?sansCin=true), lus une fois au chargement,
   * pas de champ dédié dans le panneau — ce sont des puces retirables comme les autres.
   */
  sansNumeroDossierFilter = signal(false);
  sansCinFilter = signal(false);

  /** Ouvert par défaut seulement si l'URL arrive déjà avec des filtres du panneau (voir ngOnInit). */
  filtersPanelOpen = signal(false);

  /** Nombre de filtres du panneau actifs (recherche rapide exclue). */
  readonly activeFilterCount = computed(() => {
    let count = 0;
    if (this.nomFilter()) count++;
    if (this.prenomFilter()) count++;
    if (this.numeroDossierFilter()) count++;
    if (this.telephoneFilter()) count++;
    if (this.dateNaissanceFromFilter()) count++;
    if (this.dateNaissanceToFilter()) count++;
    if (this.medecinReferentIdFilter() != null) count++;
    if (this.includeArchivedFilter()) count++;
    if (this.sansNumeroDossierFilter()) count++;
    if (this.sansCinFilter()) count++;
    return count;
  });

  readonly filterChips = computed<FilterChip[]>(() => {
    const chips: FilterChip[] = [];
    if (this.nomFilter())
      chips.push({ key: 'nom', label: this.translocoService.translate('patient.list.filters.chips.nom'), value: this.nomFilter() });
    if (this.prenomFilter())
      chips.push({
        key: 'prenom',
        label: this.translocoService.translate('patient.list.filters.chips.prenom'),
        value: this.prenomFilter()
      });
    if (this.numeroDossierFilter())
      chips.push({
        key: 'numeroDossier',
        label: this.translocoService.translate('patient.list.filters.chips.numeroDossier'),
        value: this.numeroDossierFilter()
      });
    if (this.telephoneFilter())
      chips.push({
        key: 'telephone',
        label: this.translocoService.translate('patient.list.filters.chips.telephone'),
        value: this.telephoneFilter()
      });
    if (this.dateNaissanceFromFilter())
      chips.push({
        key: 'dateNaissanceFrom',
        label: this.translocoService.translate('patient.list.filters.chips.dateNaissanceFrom'),
        value: formatDateDisplay(this.dateNaissanceFromFilter())
      });
    if (this.dateNaissanceToFilter())
      chips.push({
        key: 'dateNaissanceTo',
        label: this.translocoService.translate('patient.list.filters.chips.dateNaissanceTo'),
        value: formatDateDisplay(this.dateNaissanceToFilter())
      });
    const medecinId = this.medecinReferentIdFilter();
    if (medecinId != null) {
      const m = this.medecins().find((x) => x.id === medecinId);
      chips.push({
        key: 'medecinReferent',
        label: this.translocoService.translate('patient.list.filters.chips.medecinReferent'),
        value: m ? `${m.nom} ${m.prenom}` : `#${medecinId}`
      });
    }
    if (this.includeArchivedFilter()) {
      chips.push({
        key: 'archived',
        label: this.translocoService.translate('patient.list.filters.chips.archived'),
        value: this.translocoService.translate('patient.list.filters.chips.archivedValue')
      });
    }
    if (this.sansNumeroDossierFilter()) {
      chips.push({
        key: 'sansNumeroDossier',
        label: this.translocoService.translate('patient.list.filters.chips.sansNumeroDossier'),
        value: this.translocoService.translate('patient.list.filters.chips.oui')
      });
    }
    if (this.sansCinFilter()) {
      chips.push({
        key: 'sansCin',
        label: this.translocoService.translate('patient.list.filters.chips.sansCin'),
        value: this.translocoService.translate('patient.list.filters.chips.oui')
      });
    }
    return chips;
  });

  constructor() {
    this.iconService.addIcon(InboxOutline, PlusOutline, RollbackOutline, SearchOutline);
  }

  ngOnInit(): void {
    if (this.isAdmin()) {
      this.medecinService.getAllMedecinOptions().subscribe({
        next: (options) => this.medecins.set(options),
        error: () => {
          /* best-effort : le select "Médecin référent" du panneau restera vide en cas d'échec */
        }
      });
    }
    // panneau conservés dans l'URL (partage de lien, retour arrière, F5 — voir syncFiltersToUrl).
    const params = this.route.snapshot.queryParamMap;
    if (params.get('nom')) {
      this.nomInput.set(params.get('nom')!);
      this.nomFilter.set(params.get('nom')!);
    }
    if (params.get('prenom')) {
      this.prenomInput.set(params.get('prenom')!);
      this.prenomFilter.set(params.get('prenom')!);
    }
    if (params.get('numeroDossier')) {
      this.numeroDossierInput.set(params.get('numeroDossier')!);
      this.numeroDossierFilter.set(params.get('numeroDossier')!);
    }
    if (params.get('telephone')) {
      this.telephoneInput.set(params.get('telephone')!);
      this.telephoneFilter.set(params.get('telephone')!);
    }
    if (params.get('dateNaissanceFrom')) this.dateNaissanceFromFilter.set(params.get('dateNaissanceFrom')!);
    if (params.get('dateNaissanceTo')) this.dateNaissanceToFilter.set(params.get('dateNaissanceTo')!);
    if (params.get('medecinReferentId')) this.medecinReferentIdFilter.set(Number(params.get('medecinReferentId')));
    if (params.get('archived') === 'true') this.includeArchivedFilter.set(true);
    if (params.get('sansNumeroDossier') === 'true') this.sansNumeroDossierFilter.set(true);
    if (params.get('sansCin') === 'true') this.sansCinFilter.set(true);
    if (params.get('sort') && (params.get('direction') === 'asc' || params.get('direction') === 'desc')) {
      this.sortState.set({ field: params.get('sort')!, direction: params.get('direction') as 'asc' | 'desc' });
    }
    if (this.activeFilterCount() > 0) this.filtersPanelOpen.set(true);
    this.loadPatients();
  }

  /**
   * Reflète les filtres du panneau dans l'URL (F5, retour arrière, lien partagé). La recherche
   * rapide et la pagination n'en font pas partie : seuls les champs du panneau sont concernés.
   */
  private syncFiltersToUrl(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        nom: this.nomFilter() || null,
        prenom: this.prenomFilter() || null,
        numeroDossier: this.numeroDossierFilter() || null,
        telephone: this.telephoneFilter() || null,
        dateNaissanceFrom: this.dateNaissanceFromFilter() || null,
        dateNaissanceTo: this.dateNaissanceToFilter() || null,
        medecinReferentId: this.medecinReferentIdFilter(),
        archived: this.includeArchivedFilter() || null,
        sansNumeroDossier: this.sansNumeroDossierFilter() || null,
        sansCin: this.sansCinFilter() || null,
        sort: this.sortState()?.field || null,
        direction: this.sortState()?.direction || null
      },
      replaceUrl: true
    });
  }

  loadPatients(): void {
    this.isLoading.set(true);
    this.error.set(null);
    this.syncFiltersToUrl();

    const filters: PatientFilterParams = {};
    if (this.searchTerm()) filters.q = this.searchTerm();
    if (this.nomFilter()) filters.nom = this.nomFilter();
    if (this.prenomFilter()) filters.prenom = this.prenomFilter();
    if (this.numeroDossierFilter()) filters.numeroDossier = this.numeroDossierFilter();
    if (this.telephoneFilter()) filters.telephone = this.telephoneFilter();
    if (this.dateNaissanceFromFilter()) filters.dateNaissanceFrom = this.dateNaissanceFromFilter();
    if (this.dateNaissanceToFilter() && !this.dateNaissanceRangeInvalid()) filters.dateNaissanceTo = this.dateNaissanceToFilter();
    if (this.medecinReferentIdFilter() != null) filters.medecinReferentId = this.medecinReferentIdFilter()!;
    if (this.includeArchivedFilter()) filters.archived = true;
    if (this.sansNumeroDossierFilter()) filters.sansNumeroDossier = true;
    if (this.sansCinFilter()) filters.sansCin = true;
    if (this.sortState()) {
      filters.sort = this.sortState()!.field;
      filters.direction = this.sortState()!.direction;
    }

    this.patientService.getPatients(this.page(), this.size(), filters).subscribe({
      next: (data) => {
        this.patients.set(data.content);
        this.totalPages.set(data.totalPages);
        this.totalElements.set(data.totalElements);
        this.isLoading.set(false);
      },
      error: (err) => {
        // Message précis du backend en priorité (ou du timeout de l'intercepteur), sinon
        // libellé générique : un service indisponible reste identifiable côté utilisateur.
        this.error.set(
          extractBackendErrorMessage(err) ??
            this.translocoService.translate(
              err.status === 401
                ? 'patient.errors.unauthorized'
                : err.status === 403
                  ? 'patient.errors.forbidden'
                  : 'patient.errors.loadFailed'
            )
        );
        this.isLoading.set(false);
      }
    });
  }

  /** Appelé par le composant de pagination lors d'un changement de page. */
  onPageChange(newPage: number): void {
    this.page.set(newPage);
    this.loadPatients();
  }

  /**
   * Appelé par le composant de pagination lors d'un changement de taille de page.
   * Revient toujours à la page 0 : une page existante peut ne plus exister avec
   * la nouvelle taille (ex. page 2 avec size=50 sur 23 éléments).
   */
  onSizeChange(newSize: number): void {
    this.size.set(newSize);
    this.page.set(0);
    this.loadPatients();
  }

  /**
   * Revient à la première page avant de recharger, pour ne pas rester sur une
   * page qui n'existe plus (utilisé par la recherche, cf. onSearchChange/onResetSearch).
   */
  resetToFirstPage(): void {
    this.page.set(0);
    this.loadPatients();
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

  onNumeroDossierFilterChange(value: string): void {
    this.numeroDossierFilter.set(value);
    this.applyPanelFilters();
  }

  onTelephoneFilterChange(value: string): void {
    this.telephoneFilter.set(value);
    this.applyPanelFilters();
  }

  onDateNaissanceFromChange(value: string): void {
    this.dateNaissanceFromFilter.set(value);
    this.applyPanelFilters();
  }

  onDateNaissanceToChange(value: string): void {
    this.dateNaissanceToFilter.set(value);
    this.applyPanelFilters();
  }

  onMedecinReferentFilterChange(value: number | null): void {
    this.medecinReferentIdFilter.set(value);
    this.applyPanelFilters();
  }

  onIncludeArchivedFilterChange(value: boolean): void {
    this.includeArchivedFilter.set(value);
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
      case 'numeroDossier':
        this.numeroDossierInput.set('');
        this.numeroDossierFilter.set('');
        break;
      case 'telephone':
        this.telephoneInput.set('');
        this.telephoneFilter.set('');
        break;
      case 'dateNaissanceFrom':
        this.dateNaissanceFromFilter.set('');
        break;
      case 'dateNaissanceTo':
        this.dateNaissanceToFilter.set('');
        break;
      case 'medecinReferent':
        this.medecinReferentIdFilter.set(null);
        break;
      case 'archived':
        this.includeArchivedFilter.set(false);
        break;
      case 'sansNumeroDossier':
        this.sansNumeroDossierFilter.set(false);
        break;
      case 'sansCin':
        this.sansCinFilter.set(false);
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
    this.numeroDossierInput.set('');
    this.numeroDossierFilter.set('');
    this.telephoneInput.set('');
    this.telephoneFilter.set('');
    this.dateNaissanceFromFilter.set('');
    this.dateNaissanceToFilter.set('');
    this.medecinReferentIdFilter.set(null);
    this.includeArchivedFilter.set(false);
    this.sansNumeroDossierFilter.set(false);
    this.sansCinFilter.set(false);
    this.resetToFirstPage();
  }

  /**
   * Archive ou restaure un patient selon son état actuel (bouton unique, comme
   * pour l'actif/inactif des médecins dans Gestion.onToggle).
   */
  onArchiveToggle(patient: Patient): void {
    this.pendingPatient = patient;
    this.pendingAction = patient.archived ? 'restore' : 'archive';
    this.modalService.open(this.confirmModal, { centered: true, backdrop: 'static' }).result.then(
      (result) => {
        if (result !== 'confirm') {
          return;
        }

        const request$ = patient.archived ? this.patientService.restorePatient(patient.id) : this.patientService.archivePatient(patient.id);

        request$.subscribe({
          next: () => {
            // Recharge la page courante : l'archivage/restauration change totalElements
            // (patients archivés exclus par défaut) — un simple filtrage local désynchroniserait la pagination.
            this.loadPatients();
          },
          error: (err) => {
            const forbiddenKey = this.pendingAction === 'restore' ? 'patient.errors.restoreForbidden' : 'patient.errors.archiveForbidden';
            const failedKey = this.pendingAction === 'restore' ? 'patient.errors.restoreFailed' : 'patient.errors.archiveFailed';
            this.notificationService.showError(this.translocoService.translate(err.status === 403 ? forbiddenKey : failedKey));
          }
        });
      },
      () => {}
    );
  }

  onEdit(patient: Patient): void {
    this.editModalCmp.open(patient);
  }

  /** Après édition depuis le tableau, recharge uniquement la page courante (pas toute la liste). */
  onPatientSaved(): void {
    this.loadPatients();
  }

  onCreate(): void {
    this.createForm.reset();
    this.isCreateSubmitting.set(false);
    const modalRef = this.modalService.open(this.createModal, {
      centered: true,
      size: 'lg',
      beforeDismiss: () => confirmDiscardIfDirty(this.createForm, this.translocoService.translate('common.modal.confirmDiscard'))
    });
    focusFirstField(modalRef, 'createNom');
  }

  /**
   * Soumet le formulaire de création. Après succès, ferme le modal et recharge
   * la liste à la page 0 (le nouveau patient apparaît selon le tri backend courant).
   */
  onSubmitCreate(modal: { close: () => void }): void {
    if (this.createForm.invalid) {
      this.markFormGroupTouched(this.createForm);
      focusFirstInvalidField();
      return;
    }

    this.isCreateSubmitting.set(true);

    const patientData: PatientRequest = {
      nom: this.createForm.value.nom?.trim(),
      prenom: this.createForm.value.prenom?.trim(),
      dateNaissance: this.createForm.value.dateNaissance,
      telephone: this.createForm.value.telephone?.trim(),
      email: this.createForm.value.email?.trim().toLowerCase(),
      adresse: this.createForm.value.adresse?.trim(),
      medecinReferentId: this.createForm.value.medecinReferentId != null ? Number(this.createForm.value.medecinReferentId) : null,
      sexe: this.createForm.value.sexe || null,
      contactUrgenceNom: this.createForm.value.contactUrgenceNom?.trim() || null,
      contactUrgenceTelephone: this.createForm.value.contactUrgenceTelephone?.trim() || null,
      cin: this.createForm.value.cin?.trim() || null,
      numeroAssurance: this.createForm.value.numeroAssurance?.trim() || null
    };

    this.patientService.createPatient(patientData).subscribe({
      next: () => {
        this.isCreateSubmitting.set(false);
        modal.close();
        this.notificationService.showSuccess(this.translocoService.translate('patient.errors.createSuccess'));
        this.resetToFirstPage();
      },
      error: (err) => {
        this.notificationService.showError(
          extractBackendErrorMessage(err) ??
            this.translocoService.translate(
              err.status === 401
                ? 'common.errors.unauthorized'
                : err.status === 403
                  ? 'common.errors.adminRequired'
                  : err.status === 400
                    ? 'common.errors.invalidData'
                    : 'patient.errors.createFailed'
            )
        );
        this.isCreateSubmitting.set(false);
      }
    });
  }

  /** Validateur personnalisé : la date de naissance ne doit pas être dans le futur. */
  private dateNotFutureValidator() {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const selectedDate = new Date(control.value);
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      return selectedDate > today ? { futureDate: true } : null;
    };
  }

  private markFormGroupTouched(form: FormGroup): void {
    Object.keys(form.controls).forEach((key) => form.get(key)?.markAsTouched());
  }

  hasCreateError(fieldName: string, errorType?: string): boolean {
    const control = this.createForm.get(fieldName);
    if (!control || !control.touched) return false;
    return errorType ? control.hasError(errorType) : control.invalid;
  }

  /**
   * Comparaison utilisée par le <select> "Médecin référent" du modal de création
   * (même logique que patient-form : coercition en nombre pour absorber d'éventuels
   * écarts de type number/string renvoyés par le backend).
   */
  readonly compareMedecinById = (a: number | null, b: number | null): boolean => {
    if (a == null || b == null) return a === b;
    return Number(a) === Number(b);
  };

  get today(): string {
    return new Date().toISOString().split('T')[0];
  }

  /** Borne basse du champ date de naissance : personne n'a plus de 120 ans. */
  readonly minBirthDate = minBirthDateIso();

  readonly oldestBirthDate = '1900-01-01';
}
