import { Component, TemplateRef, ViewChild, inject, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { MedecinService } from 'src/app/core/services/medecin.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { Medecin } from 'src/app/core/models/medecin.model';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { confirmDiscardIfDirty, focusFirstField, focusFirstInvalidField } from 'src/app/core/utils/modal-ux';

// Modal d'édition factorisé, ouvert aussi bien depuis gestion (tableau) que medecin-detail (fiche) :
// un seul formulaire. Le parent déclenche l'ouverture via `open(medecin)` et écoute `saved`.
@Component({
  selector: 'app-medecin-edit-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, NgbModalModule, TranslocoModule],
  templateUrl: './medecin-edit-modal.html'
})
export class MedecinEditModalComponent {
  private readonly medecinService = inject(MedecinService);
  private readonly notificationService = inject(NotificationService);
  private readonly modalService = inject(NgbModal);
  private readonly fb = inject(FormBuilder);
  private readonly translocoService = inject(TranslocoService);

  saved = output<Medecin>();

  @ViewChild('editModal') private editModalRef!: TemplateRef<unknown>;

  private editingId: number | null = null;

  editForm: FormGroup = this.fb.group({
    nom: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    prenom: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    specialite: [''],
    numeroOrdre: [''],
    telephone: ['', [Validators.maxLength(30), Validators.pattern(/^[+()0-9 .-]*$/)]]
  });
  isEditSubmitting = signal(false);

  open(medecin: Medecin): void {
    this.editingId = medecin.id;
    this.editForm.reset();
    this.editForm.patchValue({
      nom: medecin.nom,
      prenom: medecin.prenom,
      email: medecin.email,
      specialite: medecin.specialite ?? '',
      numeroOrdre: medecin.numeroOrdre ?? '',
      telephone: medecin.telephone ?? ''
    });
    this.isEditSubmitting.set(false);
    const modalRef = this.modalService.open(this.editModalRef, {
      centered: true,
      beforeDismiss: () => confirmDiscardIfDirty(this.editForm, this.translocoService.translate('common.modal.confirmDiscard'))
    });
    focusFirstField(modalRef, 'medecinEditNom');
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

    const { nom, prenom, email, specialite, numeroOrdre, telephone } = this.editForm.value;
    this.medecinService
      .updateMedecin(id, {
        nom: nom?.trim(),
        prenom: prenom?.trim(),
        email: email?.trim(),
        specialite: specialite?.trim() ?? '',
        numeroOrdre: numeroOrdre?.trim() ?? '',
        telephone: telephone?.trim() ?? ''
      })
      .subscribe({
        next: (updated) => {
          this.isEditSubmitting.set(false);
          modal.close();
          this.notificationService.showSuccess(this.translocoService.translate('medecin.errors.updateSuccess'));
          this.saved.emit(updated);
        },
        error: (err) => {
          this.notificationService.showError(
            extractBackendErrorMessage(err) ??
              this.translocoService.translate(
                err.status === 401
                  ? 'common.errors.unauthorized'
                  : err.status === 403
                    ? 'medecin.errors.updateForbidden'
                    : err.status === 400
                      ? 'common.errors.invalidData'
                      : 'medecin.errors.updateFailed'
              )
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
}
