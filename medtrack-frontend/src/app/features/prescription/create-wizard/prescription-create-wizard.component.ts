import { Component, TemplateRef, ViewChild, inject, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import { LeftOutline, FileTextOutline } from '@ant-design/icons-angular/icons';
import { DebounceInput } from 'src/app/theme/shared/directives/debounce-input';
import { PatientService } from 'src/app/core/services/patient.service';
import { MedicalRecordService } from 'src/app/core/services/medical-record.service';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { Prescription } from 'src/app/core/models/prescription.model';
import { PatientOption } from 'src/app/core/models/appointment.model';
import { Consultation } from 'src/app/core/models/medical-record.model';
import { PrescriptionEditModalComponent, PrescriptionFormModel } from '../edit-modal/prescription-edit-modal.component';
import { DateFormatPipe } from 'src/app/core/pipes/date-format.pipe';

@Component({
  selector: 'app-prescription-create-wizard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    NgbModalModule,
    TranslocoModule,
    IconDirective,
    DebounceInput,
    PrescriptionEditModalComponent,
    DateFormatPipe
  ],
  templateUrl: './prescription-create-wizard.component.html',
  styleUrl: './prescription-create-wizard.component.scss'
})
export class PrescriptionCreateWizardComponent {
  private readonly patientService = inject(PatientService);
  private readonly medicalRecordService = inject(MedicalRecordService);
  private readonly translocoService = inject(TranslocoService);
  private readonly modalService = inject(NgbModal);
  private readonly iconService = inject(IconService);
  private readonly router = inject(Router);

  @ViewChild('wizardModal') wizardModalRef!: TemplateRef<unknown>;
  @ViewChild(PrescriptionEditModalComponent) editModalCmp!: PrescriptionEditModalComponent;

  saved = output<Prescription>();

  isRenewal = signal(false);
  step = signal<'patient' | 'consultation'>('patient');

  patientSearchInput = signal('');
  patientSearchResults = signal<PatientOption[]>([]);
  patientSearchLoading = signal(false);
  selectedPatient = signal<PatientOption | null>(null);

  consultations = signal<Consultation[]>([]);
  consultationsLoading = signal(false);
  consultationsError = signal<string | null>(null);
  noRecord = signal(false);

  private prefill: Partial<PrescriptionFormModel> | null = null;

  constructor() {
    this.iconService.addIcon(LeftOutline, FileTextOutline);
  }

  openCreate(): void {
    this.isRenewal.set(false);
    this.prefill = null;
    this.patientSearchInput.set('');
    this.patientSearchResults.set([]);
    this.selectedPatient.set(null);
    this.step.set('patient');
    this.modalService.open(this.wizardModalRef, { centered: true, backdrop: true, size: 'lg' });
  }

  openRenewal(prescription: Prescription): void {
    this.isRenewal.set(true);
    this.prefill = {
      medicament: prescription.medicament,
      posologie: prescription.posologie,
      dureeJours: prescription.dureeJours,
      instructions: prescription.instructions ?? '',
      renouvelable: prescription.renouvelable
    };
    const patient: PatientOption = {
      id: prescription.patientId,
      nom: prescription.patientNom ?? '',
      prenom: prescription.patientPrenom ?? ''
    };
    this.selectedPatient.set(patient);
    this.step.set('consultation');
    this.loadConsultations(patient.id);
    this.modalService.open(this.wizardModalRef, { centered: true, backdrop: true, size: 'lg' });
  }

  onPatientSearchChange(term: string): void {
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
    this.selectedPatient.set(p);
    this.patientSearchResults.set([]);
    this.step.set('consultation');
    this.loadConsultations(p.id);
  }

  backToPatientStep(): void {
    this.step.set('patient');
    this.consultations.set([]);
    this.consultationsError.set(null);
    this.noRecord.set(false);
  }

  private loadConsultations(patientId: number): void {
    this.consultationsLoading.set(true);
    this.consultationsError.set(null);
    this.noRecord.set(false);
    this.consultations.set([]);

    this.medicalRecordService.getByPatient(patientId).subscribe({
      next: (record) => {
        this.medicalRecordService.getConsultations(record.id, 0, 20).subscribe({
          next: (page) => {
            this.consultations.set(page.content);
            this.consultationsLoading.set(false);
          },
          error: (err) => {
            this.consultationsLoading.set(false);
            this.consultationsError.set(
              extractBackendErrorMessage(err) ?? this.translocoService.translate('prescription.createWizard.stepConsultation.loadError')
            );
          }
        });
      },
      error: (err) => {
        this.consultationsLoading.set(false);
        if (err.status === 404) {
          this.noRecord.set(true);
          return;
        }
        this.consultationsError.set(
          extractBackendErrorMessage(err) ?? this.translocoService.translate('prescription.createWizard.stepConsultation.loadError')
        );
      }
    });
  }

  retryLoadConsultations(): void {
    const p = this.selectedPatient();
    if (p) {
      this.loadConsultations(p.id);
    }
  }

  selectConsultation(c: Consultation, modal: { close: () => void }): void {
    modal.close();
    this.editModalCmp.openCreate(c.id, this.prefill ?? undefined, this.isRenewal());
  }

  goCreateConsultation(modal: { close: () => void }): void {
    const p = this.selectedPatient();
    modal.close();
    if (p) {
      this.router.navigate(['/patients', p.id]);
    }
  }

  onPrescriptionSaved(p: Prescription): void {
    this.saved.emit(p);
  }
}
