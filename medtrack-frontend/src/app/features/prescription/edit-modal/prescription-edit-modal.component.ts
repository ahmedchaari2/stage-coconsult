import { Component, TemplateRef, ViewChild, inject, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { PrescriptionService } from 'src/app/core/services/prescription.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { Prescription, PrescriptionRequest } from 'src/app/core/models/prescription.model';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { confirmDiscardIfDirty, focusFirstField, focusFirstInvalidField } from 'src/app/core/utils/modal-ux';

export interface PrescriptionFormModel {
  medicament: string;
  posologie: string;
  dureeJours: number | null;
  instructions: string;
  renouvelable: boolean;
}

@Component({
  selector: 'app-prescription-edit-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslocoModule],
  templateUrl: './prescription-edit-modal.component.html',
  styleUrl: './prescription-edit-modal.component.scss'
})
export class PrescriptionEditModalComponent {
  private readonly prescriptionService = inject(PrescriptionService);
  private readonly notificationService = inject(NotificationService);
  private readonly modalService = inject(NgbModal);
  private readonly translocoService = inject(TranslocoService);

  @ViewChild('editModal') editModalRef!: TemplateRef<unknown>;
  @ViewChild('prescriptionEditForm') private formRef?: NgForm;

  saved = output<Prescription>();

  submitted = signal(false);
  submitting = signal(false);
  mode = signal<'create' | 'edit'>('edit');

  private editingId: number | null = null;
  private consultationId: number | null = null;
  private renewal = false;
  model: PrescriptionFormModel = { medicament: '', posologie: '', dureeJours: null, instructions: '', renouvelable: false };

  open(prescription: Prescription): void {
    this.mode.set('edit');
    this.editingId = prescription.id;
    this.consultationId = null;
    this.renewal = false;
    this.model = {
      medicament: prescription.medicament,
      posologie: prescription.posologie,
      dureeJours: prescription.dureeJours,
      instructions: prescription.instructions ?? '',
      renouvelable: prescription.renouvelable
    };
    this.submitted.set(false);
    this.openModal();
  }

  openCreate(consultationId: number, prefill?: Partial<PrescriptionFormModel>, renewal = false): void {
    this.mode.set('create');
    this.editingId = null;
    this.consultationId = consultationId;
    this.renewal = renewal;
    this.model = {
      medicament: prefill?.medicament ?? '',
      posologie: prefill?.posologie ?? '',
      dureeJours: prefill?.dureeJours ?? null,
      instructions: prefill?.instructions ?? '',
      renouvelable: prefill?.renouvelable ?? false
    };
    this.submitted.set(false);
    this.openModal();
  }

  private openModal(): void {
    const modalRef = this.modalService.open(this.editModalRef, {
      centered: true,
      beforeDismiss: () =>
        confirmDiscardIfDirty(this.formRef ?? { dirty: false }, this.translocoService.translate('common.modal.confirmDiscard'))
    });
    focusFirstField(modalRef, 'editPrescriptionMedicament');
  }

  onSubmit(form: NgForm, modal: { close: () => void }): void {
    this.submitted.set(true);

    if (form.invalid) {
      focusFirstInvalidField();
      return;
    }

    const request: PrescriptionRequest = {
      medicament: this.model.medicament.trim(),
      posologie: this.model.posologie.trim(),
      dureeJours: this.model.dureeJours,
      instructions: this.model.instructions?.trim() || null,
      renouvelable: this.model.renouvelable
    };

    this.submitting.set(true);

    const isCreate = this.mode() === 'create';
    const request$ = isCreate
      ? this.prescriptionService.create(this.consultationId!, request, this.renewal)
      : this.prescriptionService.update(this.editingId!, request);

    request$.subscribe({
      next: (saved) => {
        this.submitting.set(false);
        modal.close();
        this.notificationService.showSuccess(
          this.translocoService.translate(isCreate ? 'prescription.success.created' : 'prescription.success.updated')
        );
        this.saved.emit(saved);
      },
      error: (err) => {
        this.submitting.set(false);
        this.notificationService.showError(
          extractBackendErrorMessage(err) ??
            this.translocoService.translate(
              err.status === 403
                ? 'prescription.errors.actionForbidden'
                : err.status === 400
                  ? 'prescription.errors.invalidData'
                  : isCreate
                    ? 'prescription.errors.createFailed'
                    : 'prescription.errors.updateFailed'
            )
        );
      }
    });
  }
}
