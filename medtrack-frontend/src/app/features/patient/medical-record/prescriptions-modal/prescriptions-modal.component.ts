import { Component, TemplateRef, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { Observable } from 'rxjs';
import { NgbModal, NgbModalModule, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import {
  EditOutline,
  InboxOutline,
  RollbackOutline,
  PlusOutline,
  MedicineBoxOutline,
  PrinterOutline
} from '@ant-design/icons-angular/icons';
import { PrescriptionService } from 'src/app/core/services/prescription.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { Prescription, PrescriptionRequest } from 'src/app/core/models/prescription.model';
import { Consultation } from 'src/app/core/models/medical-record.model';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { downloadBlob } from 'src/app/core/utils/file-download';
import { PrescriptionEditModalComponent } from 'src/app/features/prescription/edit-modal/prescription-edit-modal.component';

interface PrescriptionFormModel {
  medicament: string;
  posologie: string;
  dureeJours: number | null;
  instructions: string;
  renouvelable: boolean;
}

/**
 * Modal listant les prescriptions d'une consultation, avec ajout inline (panneau au-dessus
 * de la liste), édition via le modal partagé PrescriptionEditModalComponent (même composant
 * que la page transversale /prescriptions) et archivage/restauration. Ouvert depuis
 * medical-record.ts (action "Prescriptions" de la colonne Actions).
 */
@Component({
  selector: 'app-prescriptions-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, NgbModalModule, NgbTooltip, TranslocoModule, IconDirective, PrescriptionEditModalComponent],
  templateUrl: './prescriptions-modal.component.html',
  styleUrl: './prescriptions-modal.component.scss'
})
export class PrescriptionsModalComponent {
  private readonly prescriptionService = inject(PrescriptionService);
  private readonly notificationService = inject(NotificationService);
  private readonly modalService = inject(NgbModal);
  private readonly translocoService = inject(TranslocoService);
  private readonly iconService = inject(IconService);

  @ViewChild('prescriptionsModal') prescriptionsModalRef!: TemplateRef<unknown>;
  @ViewChild('confirmArchiveModal') confirmArchiveModalRef!: TemplateRef<unknown>;
  @ViewChild(PrescriptionEditModalComponent) editModalCmp!: PrescriptionEditModalComponent;

  consultation = signal<Consultation | null>(null);
  prescriptions = signal<Prescription[]>([]);
  loading = signal(false);
  loadError = signal<string | null>(null);
  /** Filtre exact (pas "inclusif") côté backend, même contrat que showArchivedFilter des consultations. */
  showArchived = signal(false);

  formOpen = signal(false);
  formSubmitted = signal(false);
  formSubmitting = signal(false);
  formModel: PrescriptionFormModel = { medicament: '', posologie: '', dureeJours: null, instructions: '', renouvelable: false };

  // --- Confirmation archivage/restauration ---
  pendingPrescription: Prescription | null = null;
  pendingAction: 'archive' | 'restore' = 'archive';

  // Nombre de prescriptions actives, mis à jour uniquement quand la liste affichée est celle
  // des actives : le PDF ne contient que celles-là, et basculer sur "archivées" ne doit pas
  activeCount = signal(0);
  ordonnanceLoading = signal(false);

  constructor() {
    this.iconService.addIcon(EditOutline, InboxOutline, RollbackOutline, PlusOutline, MedicineBoxOutline, PrinterOutline);
  }

  open(consultation: Consultation): void {
    this.consultation.set(consultation);
    this.showArchived.set(false);
    this.formOpen.set(false);
    this.activeCount.set(0);
    this.loadPrescriptions();
    this.modalService.open(this.prescriptionsModalRef, { centered: true, backdrop: 'static', size: 'lg' });
  }

  loadPrescriptions(): void {
    const c = this.consultation();
    if (!c) return;

    this.loading.set(true);
    this.loadError.set(null);

    this.prescriptionService.listForConsultation(c.id, this.showArchived()).subscribe({
      next: (data) => {
        this.prescriptions.set(data);
        if (!this.showArchived()) {
          this.activeCount.set(data.length);
        }
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.loadError.set(extractBackendErrorMessage(err) ?? this.translocoService.translate('prescription.errors.loadFailed'));
      }
    });
  }

  onShowArchivedChange(value: boolean): void {
    this.showArchived.set(value);
    this.loadPrescriptions();
  }

  openCreateForm(): void {
    this.formModel = { medicament: '', posologie: '', dureeJours: null, instructions: '', renouvelable: false };
    this.formSubmitted.set(false);
    this.formOpen.set(true);
  }

  openEditForm(p: Prescription): void {
    this.editModalCmp.open(p);
  }

  onPrescriptionSaved(): void {
    this.loadPrescriptions();
  }

  cancelForm(): void {
    this.formOpen.set(false);
  }

  onSubmitForm(form: NgForm): void {
    this.formSubmitted.set(true);

    if (form.invalid) {
      return;
    }

    const c = this.consultation();
    if (!c) return;

    const request: PrescriptionRequest = {
      medicament: this.formModel.medicament.trim(),
      posologie: this.formModel.posologie.trim(),
      dureeJours: this.formModel.dureeJours,
      instructions: this.formModel.instructions?.trim() || null,
      renouvelable: this.formModel.renouvelable
    };

    this.formSubmitting.set(true);

    this.prescriptionService.create(c.id, request).subscribe({
      next: () => {
        this.formSubmitting.set(false);
        this.formOpen.set(false);
        this.notificationService.showSuccess(this.translocoService.translate('prescription.success.created'));
        this.loadPrescriptions();
      },
      error: (err) => {
        this.formSubmitting.set(false);
        this.notificationService.showError(
          extractBackendErrorMessage(err) ??
            this.translocoService.translate(
              err.status === 403
                ? 'prescription.errors.actionForbidden'
                : err.status === 400
                  ? 'prescription.errors.invalidData'
                  : 'prescription.errors.createFailed'
            )
        );
      }
    });
  }

  /** Télécharge l'ordonnance PDF de la consultation (prescriptions actives uniquement). */
  onPrintOrdonnance(): void {
    const c = this.consultation();
    if (!c || this.activeCount() === 0) {
      return;
    }

    this.ordonnanceLoading.set(true);
    this.prescriptionService.downloadOrdonnance(c.id).subscribe({
      next: (blob) => {
        this.ordonnanceLoading.set(false);
        downloadBlob(blob, `ordonnance-${c.id}.pdf`);
      },
      error: (err) => {
        this.ordonnanceLoading.set(false);
        this.notificationService.showError(
          extractBackendErrorMessage(err) ??
            this.translocoService.translate(
              err.status === 403 ? 'prescription.errors.actionForbidden' : 'prescription.errors.ordonnanceFailed'
            )
        );
      }
    });
  }

  /** Archive ou restaure une prescription selon son état actuel (même pattern que les consultations). */
  onArchiveToggle(p: Prescription): void {
    this.pendingPrescription = p;
    this.pendingAction = p.archived ? 'restore' : 'archive';
    this.modalService.open(this.confirmArchiveModalRef, { centered: true, backdrop: 'static' }).result.then(
      (result) => {
        if (result !== 'confirm') {
          return;
        }

        // Types de retour différents (restore -> Prescription, archive -> void) : annotation
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
