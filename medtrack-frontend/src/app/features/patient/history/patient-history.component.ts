import { Component, computed, effect, inject, input, signal, TemplateRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgbModal, NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoModule } from '@jsverse/transloco';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import { FileTextOutline, CalendarOutline, MedicineBoxOutline, HistoryOutline } from '@ant-design/icons-angular/icons';
import { catchError, forkJoin, map, Observable, of, switchMap } from 'rxjs';
import { MedicalRecordService } from 'src/app/core/services/medical-record.service';
import { AppointmentService } from 'src/app/core/services/appointment.service';
import { PrescriptionService } from 'src/app/core/services/prescription.service';
import { AuthService } from 'src/app/core/services/auth.service';
import { Consultation } from 'src/app/core/models/medical-record.model';
import { Appointment } from 'src/app/core/models/appointment.model';
import { Prescription } from 'src/app/core/models/prescription.model';
import { PageDTO } from 'src/app/core/models/page.model';
import { MedecinOption } from 'src/app/core/models/medecin.model';
import { PrescriptionsModalComponent } from '../medical-record/prescriptions-modal/prescriptions-modal.component';
import { AppointmentStatutBadgeComponent } from 'src/app/theme/shared/components/appointment-statut-badge/appointment-statut-badge';
import { Pagination } from 'src/app/theme/shared/components/pagination/pagination';
import { formatDateDisplay, formatDateTimeDisplay } from 'src/app/core/utils/date-format';
import { DateFormatPipe } from 'src/app/core/pipes/date-format.pipe';
import { SpinnerComponent } from 'src/app/theme/shared/components/spinner/spinner.component';
import { Spinkit } from 'src/app/theme/shared/components/spinner/spinkits';

export type HistoryType = 'consultation' | 'appointment' | 'prescription';

/**
 * Entrée unifiée de la frise. `date` est la clé de tri (ISO brut) : une date seule pour une
 * consultation ou une prescription, une date-heure pour un rendez-vous — comparables telles
 * quelles en ordre lexicographique puisque toutes commencent par YYYY-MM-DD.
 */
interface HistoryEntry {
  type: HistoryType;
  date: string;
  title: string;
  subtitle: string | null;
  consultation?: Consultation;
  appointment?: Appointment;
  prescription?: Prescription;
}

type SourceResult<T> = { ok: true; page: PageDTO<T> } | { ok: false };

// Le backend plafonne une page à 100 éléments : c'est aussi la profondeur d'historique
const MAX_PER_SOURCE = 100;

@Component({
  selector: 'app-patient-history',
  standalone: true,
  imports: [
    CommonModule,
    NgbModalModule,
    TranslocoModule,
    IconDirective,
    PrescriptionsModalComponent,
    AppointmentStatutBadgeComponent,
    DateFormatPipe,
    Pagination,
    SpinnerComponent
  ],
  templateUrl: './patient-history.component.html',
  styleUrl: './patient-history.component.scss'
})
export class PatientHistoryComponent {
  readonly spinkit = Spinkit.skLine;

  private readonly medicalRecordService = inject(MedicalRecordService);
  private readonly appointmentService = inject(AppointmentService);
  private readonly prescriptionService = inject(PrescriptionService);
  private readonly authService = inject(AuthService);
  private readonly modalService = inject(NgbModal);
  private readonly iconService = inject(IconService);

  @ViewChild('detailModal') detailModal!: TemplateRef<unknown>;
  @ViewChild(PrescriptionsModalComponent) prescriptionsModalCmp!: PrescriptionsModalComponent;

  readonly isAdmin = computed(() => this.authService.currentUser()?.role === 'ADMIN');

  patientId = input.required<number>();
  medecins = input.required<MedecinOption[]>();

  entries = signal<HistoryEntry[]>([]);
  isLoading = signal(true);
  truncated = signal(false);

  consultationsError = signal(false);
  appointmentsError = signal(false);
  prescriptionsError = signal(false);
  readonly sourceErrorCount = computed(
    () => (this.consultationsError() ? 1 : 0) + (this.appointmentsError() ? 1 : 0) + (this.prescriptionsError() ? 1 : 0)
  );

  readonly typeFilter = signal<HistoryType | 'all'>('all');
  selected = signal<HistoryEntry | null>(null);

  // Pagination purement client : toutes les entrées sont déjà en mémoire (chargées d'un coup,
  // plafonnées à MAX_PER_SOURCE par source), pas besoin d'aller-retour serveur pour une page.
  readonly page = signal(0);
  readonly size = signal(10);
  readonly sizeOptions = [10, 25, 50, 100];

  readonly counts = computed(() => {
    const all = this.entries();
    return {
      all: all.length,
      consultation: all.filter((e) => e.type === 'consultation').length,
      appointment: all.filter((e) => e.type === 'appointment').length,
      prescription: all.filter((e) => e.type === 'prescription').length
    };
  });

  readonly visibleEntries = computed(() => {
    const filter = this.typeFilter();
    return filter === 'all' ? this.entries() : this.entries().filter((e) => e.type === filter);
  });

  readonly totalPages = computed(() => Math.ceil(this.visibleEntries().length / this.size()));

  readonly pagedEntries = computed(() => {
    const start = this.page() * this.size();
    return this.visibleEntries().slice(start, start + this.size());
  });

  constructor() {
    this.iconService.addIcon(FileTextOutline, CalendarOutline, MedicineBoxOutline, HistoryOutline);

    effect(() => {
      this.patientId();
      this.typeFilter.set('all');
      this.page.set(0);
      this.load();
    });
  }

  load(): void {
    const id = this.patientId();
    this.isLoading.set(true);
    this.consultationsError.set(false);
    this.appointmentsError.set(false);
    this.prescriptionsError.set(false);
    this.truncated.set(false);

    // Un patient sans dossier médical (404) n'a simplement pas de consultation : cas normal,
    // basculent seulement le bloc consultations en indisponible (voir wrap ci-dessous).
    const consultations$ = this.medicalRecordService.getByPatient(id).pipe(
      switchMap((record) => this.medicalRecordService.getConsultations(record.id, 0, MAX_PER_SOURCE)),
      catchError((err) => {
        if (err.status === 404) {
          return of<PageDTO<Consultation>>({ content: [], page: 0, size: MAX_PER_SOURCE, totalPages: 0, totalElements: 0 });
        }
        throw err;
      })
    );

    // forkJoin. Les sources qui ont répondu restent affichées.
    forkJoin({
      consultations: this.wrapSource(consultations$),
      appointments: this.wrapSource(this.appointmentService.getAppointments(0, MAX_PER_SOURCE, { patientId: id })),
      prescriptions: this.wrapSource(this.prescriptionService.search(0, MAX_PER_SOURCE, { patientId: id }))
    }).subscribe(({ consultations, appointments, prescriptions }) => {
      const entries: HistoryEntry[] = [];
      let truncated = false;

      if (consultations.ok) {
        entries.push(...consultations.page.content.map((c) => this.toConsultationEntry(c)));
        truncated = truncated || consultations.page.totalElements > MAX_PER_SOURCE;
      } else {
        this.consultationsError.set(true);
      }
      if (appointments.ok) {
        entries.push(...appointments.page.content.map((a) => this.toAppointmentEntry(a)));
        truncated = truncated || appointments.page.totalElements > MAX_PER_SOURCE;
      } else {
        this.appointmentsError.set(true);
      }
      if (prescriptions.ok) {
        entries.push(...prescriptions.page.content.map((p) => this.toPrescriptionEntry(p)));
        truncated = truncated || prescriptions.page.totalElements > MAX_PER_SOURCE;
      } else {
        this.prescriptionsError.set(true);
      }

      this.entries.set(entries.sort((a, b) => b.date.localeCompare(a.date)));
      this.truncated.set(truncated);
      this.isLoading.set(false);
    });
  }

  private wrapSource<T>(source$: Observable<PageDTO<T>>): Observable<SourceResult<T>> {
    return source$.pipe(
      map((page) => ({ ok: true as const, page })),
      catchError(() => of({ ok: false as const }))
    );
  }

  private toConsultationEntry(c: Consultation): HistoryEntry {
    return {
      type: 'consultation',
      date: c.consultationDate,
      title: c.motif,
      subtitle: c.diagnostic || null,
      consultation: c
    };
  }

  private toAppointmentEntry(a: Appointment): HistoryEntry {
    return { type: 'appointment', date: a.dateHeure, title: a.motif, subtitle: null, appointment: a };
  }

  /**
   * Une prescription est datée par sa consultation (jointure backend). Si celle-ci n'a pas pu
   * être résolue, la prescription reste affichée mais sans date, en fin de frise.
   */
  private toPrescriptionEntry(p: Prescription): HistoryEntry {
    return {
      type: 'prescription',
      date: p.consultationDate ?? '',
      title: p.medicament,
      subtitle: p.posologie,
      prescription: p
    };
  }

  onFilterChange(type: HistoryType | 'all'): void {
    this.typeFilter.set(type);
    this.page.set(0);
  }

  onPageChange(page: number): void {
    this.page.set(page);
  }

  onSizeChange(size: number): void {
    this.size.set(size);
    this.page.set(0);
  }

  openDetail(entry: HistoryEntry): void {
    this.selected.set(entry);
    this.modalService.open(this.detailModal, { centered: true, size: 'lg' });
  }

  openPrescriptions(c: Consultation, modal: { close: () => void }): void {
    modal.close();
    this.prescriptionsModalCmp.open(c);
  }

  medecinDisplay(medecinId: number | null | undefined): string {
    if (medecinId == null) return '—';
    const med = this.medecins().find((m) => Number(m.id) === Number(medecinId));
    return med ? `${med.nom} ${med.prenom}` : `Médecin #${medecinId}`;
  }

  /** Nom du médecin d'un rendez-vous : déjà enrichi par le backend, sinon repli sur la liste. */
  appointmentMedecin(a: Appointment): string {
    return a.medecinNom ? `${a.medecinNom} ${a.medecinPrenom}` : this.medecinDisplay(a.medecinId);
  }

  formatEntryDate(entry: HistoryEntry): string {
    if (!entry.date) return '—';
    return entry.type === 'appointment' ? formatDateTimeDisplay(entry.date) : formatDateDisplay(entry.date);
  }

  tensionDisplay(c: Consultation): string {
    if (c.tensionArterielleSystolique == null || c.tensionArterielleDiastolique == null) return '—';
    return `${c.tensionArterielleSystolique}/${c.tensionArterielleDiastolique} mmHg`;
  }
}
