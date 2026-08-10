import { Component, TemplateRef, ViewChild, computed, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors, ReactiveFormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { PatientService } from 'src/app/core/services/patient.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { AuthService } from 'src/app/core/services/auth.service';
import { Patient, PatientRequest, SexePatient } from 'src/app/core/models/patient.model';
import { MedecinOption } from 'src/app/core/models/medecin.model';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { confirmDiscardIfDirty, focusFirstField, focusFirstInvalidField } from 'src/app/core/utils/modal-ux';
import { DateInputComponent } from 'src/app/theme/shared/components/date-input/date-input';
import { minBirthDateIso } from 'src/app/core/utils/date-bounds';

/**
 * Modal d'édition d'un patient, factorisé pour être ouvert aussi bien depuis patient-list
 * (tableau) que depuis patient-detail (fiche) — mêmes champs/validateurs, une seule
 * implémentation. Le parent fournit `medecins` (déjà chargé pour ses propres besoins, pas
 * de second appel réseau ici) et déclenche l'ouverture via `open(patient)` ; `saved`
 * notifie le parent une fois l'enregistrement réussi.
 */
@Component({
  selector: 'app-patient-edit-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, NgbModalModule, TranslocoModule, DateInputComponent],
  templateUrl: './patient-edit-modal.html'
})
export class PatientEditModalComponent {
  private readonly patientService = inject(PatientService);
  private readonly notificationService = inject(NotificationService);
  private readonly authService = inject(AuthService);
  private readonly modalService = inject(NgbModal);
  private readonly fb = inject(FormBuilder);
  private readonly translocoService = inject(TranslocoService);

  medecins = input.required<MedecinOption[]>();
  saved = output<Patient>();

  // Rôle courant : réservé aux champs ADMIN uniquement (ex. "Médecin référent").
  readonly isAdmin = computed(() => this.authService.currentUser()?.role === 'ADMIN');

  @ViewChild('editModal') private editModalRef!: TemplateRef<unknown>;

  private editingId: number | null = null;

  // Valeurs exactes de l'enum backend SexePatient (EnumType.STRING, valeur envoyée telle quelle)
  readonly sexeOptions: SexePatient[] = ['HOMME', 'FEMME'];

  editForm: FormGroup = this.fb.group({
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
  isEditSubmitting = signal(false);

  open(patient: Patient): void {
    this.editingId = patient.id;
    this.editForm.reset();
    this.editForm.patchValue({
      nom: patient.nom,
      prenom: patient.prenom,
      dateNaissance: patient.dateNaissance,
      telephone: patient.telephone,
      email: patient.email,
      adresse: patient.adresse,
      medecinReferentId: patient.medecinReferentId != null ? Number(patient.medecinReferentId) : null,
      sexe: patient.sexe ?? null,
      contactUrgenceNom: patient.contactUrgenceNom ?? '',
      contactUrgenceTelephone: patient.contactUrgenceTelephone ?? '',
      cin: patient.cin ?? '',
      numeroAssurance: patient.numeroAssurance ?? ''
    });
    this.isEditSubmitting.set(false);
    const modalRef = this.modalService.open(this.editModalRef, {
      centered: true,
      size: 'lg',
      beforeDismiss: () => confirmDiscardIfDirty(this.editForm, this.translocoService.translate('common.modal.confirmDiscard'))
    });
    focusFirstField(modalRef, 'editNom');
  }

  onSubmit(modal: { close: () => void }): void {
    if (this.editForm.invalid) {
      Object.keys(this.editForm.controls).forEach((key) => this.editForm.get(key)?.markAsTouched());
      focusFirstInvalidField();
      return;
    }

    const id = this.editingId;
    if (!id) return;

    this.isEditSubmitting.set(true);

    const patientData: PatientRequest = {
      nom: this.editForm.value.nom?.trim(),
      prenom: this.editForm.value.prenom?.trim(),
      dateNaissance: this.editForm.value.dateNaissance,
      telephone: this.editForm.value.telephone?.trim(),
      email: this.editForm.value.email?.trim().toLowerCase(),
      adresse: this.editForm.value.adresse?.trim(),
      medecinReferentId: this.editForm.value.medecinReferentId != null ? Number(this.editForm.value.medecinReferentId) : null,
      sexe: this.editForm.value.sexe || null,
      contactUrgenceNom: this.editForm.value.contactUrgenceNom?.trim() || null,
      contactUrgenceTelephone: this.editForm.value.contactUrgenceTelephone?.trim() || null,
      cin: this.editForm.value.cin?.trim() || null,
      numeroAssurance: this.editForm.value.numeroAssurance?.trim() || null
    };

    this.patientService.updatePatient(id, patientData).subscribe({
      next: (updated) => {
        this.isEditSubmitting.set(false);
        modal.close();
        this.notificationService.showSuccess('Patient modifié avec succès.');
        this.saved.emit(updated);
      },
      error: (err) => {
        this.notificationService.showError(
          extractBackendErrorMessage(err) ??
            (err.status === 401
              ? 'Non autorisé. Veuillez vous reconnecter.'
              : err.status === 403
                ? 'Accès refusé. Droits administrateur requis.'
                : err.status === 400
                  ? 'Données invalides. Vérifiez le formulaire.'
                  : 'Erreur lors de la modification du patient.')
        );
        this.isEditSubmitting.set(false);
      }
    });
  }

  hasError(fieldName: string, errorType?: string): boolean {
    const control = this.editForm.get(fieldName);
    if (!control || !control.touched) return false;
    return errorType ? control.hasError(errorType) : control.invalid;
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

  readonly compareMedecinById = (a: number | null, b: number | null): boolean => {
    if (a == null || b == null) return a === b;
    return Number(a) === Number(b);
  };

  get today(): string {
    return new Date().toISOString().split('T')[0];
  }

  /** Borne basse du champ date de naissance : personne n'a plus de 120 ans. */
  readonly minBirthDate = minBirthDateIso();
}
