import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { NgApexchartsModule, ApexOptions } from 'ng-apexcharts';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import {
  CalendarOutline,
  TeamOutline,
  FileTextOutline,
  MedicineBoxOutline,
  WarningOutline,
  RightOutline,
  ReloadOutline
} from '@ant-design/icons-angular/icons';
import { CardComponent } from 'src/app/theme/shared/components/card/card.component';
import { DashboardService } from 'src/app/core/services/dashboard.service';
import { MedecinDashboard } from 'src/app/core/models/dashboard.model';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { appointmentStatutBadgeClass } from 'src/app/core/utils/appointment-statut';
import { AppointmentStatut } from 'src/app/core/models/appointment.model';
import { DateFormatPipe } from 'src/app/core/pipes/date-format.pipe';
import { TimeFormatPipe } from 'src/app/core/pipes/time-format.pipe';
import { Pagination } from 'src/app/theme/shared/components/pagination/pagination';

const STATUTS: AppointmentStatut[] = ['PLANIFIE', 'CONFIRME', 'ANNULE', 'HONORE'];

@Component({
  selector: 'app-medecin-dashboard',
  standalone: true,
  imports: [CommonModule, TranslocoModule, IconDirective, CardComponent, NgApexchartsModule, DateFormatPipe, TimeFormatPipe, Pagination],
  templateUrl: './medecin-dashboard.component.html',
  styleUrl: '../dashboard.shared.scss'
})
export class MedecinDashboardComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly translocoService = inject(TranslocoService);
  private readonly iconService = inject(IconService);
  private readonly router = inject(Router);

  readonly statutOrder = STATUTS;
  readonly statutBadgeClass = (s: string) => appointmentStatutBadgeClass(s as AppointmentStatut);

  data = signal<MedecinDashboard | null>(null);
  isLoading = signal(true);
  error = signal<string | null>(null);

  readonly dashboardPageSizeOptions = [5, 10, 25];
  patientsSansDossierPage = signal(0);
  patientsSansDossierSize = signal(5);
  readonly traitementsPreview = computed(() => (this.data()?.traitementsAEcheance.items ?? []).slice(0, 5));
  readonly patientsSansDossierItems = computed(() => {
    const items = this.data()?.patientsSansDossier.items ?? [];
    const start = this.patientsSansDossierPage() * this.patientsSansDossierSize();
    return items.slice(start, start + this.patientsSansDossierSize());
  });
  readonly patientsSansDossierTotalPages = computed(() =>
    Math.ceil((this.data()?.patientsSansDossier.items.length ?? 0) / this.patientsSansDossierSize())
  );

  readonly rdvChart = computed<Partial<ApexOptions>>(() => {
    const section = this.data()?.rendezVousJour;
    const values = STATUTS.map((s) => section?.parStatut?.[s] ?? 0);
    return {
      chart: { type: 'donut', height: 260, background: 'transparent' },
      series: values,
      labels: STATUTS.map((s) => this.translocoService.translate('appointment.statut.' + s)),
      colors: ['#1677ff', '#52c41a', '#8c8c8c', '#237804'],
      legend: { position: 'bottom' },
      dataLabels: { enabled: false },
      stroke: { width: 0 }
    };
  });

  constructor() {
    this.iconService.addIcon(
      CalendarOutline,
      TeamOutline,
      FileTextOutline,
      MedicineBoxOutline,
      WarningOutline,
      RightOutline,
      ReloadOutline
    );
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.isLoading.set(true);
    this.error.set(null);
    this.dashboardService.getMedecinDashboard().subscribe({
      next: (data) => {
        this.data.set(data);
        this.patientsSansDossierPage.set(0);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.error.set(
          extractBackendErrorMessage(err) ??
            this.translocoService.translate(err.status === 403 ? 'dashboard.errors.forbidden' : 'dashboard.errors.loadFailed')
        );
        this.isLoading.set(false);
      }
    });
  }

  readonly hasRdv = computed(() => (this.data()?.rendezVousJour?.total ?? 0) > 0);

  private today(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }

  goToday(): void {
    this.router.navigate(['/appointments'], { queryParams: { date: this.today() } });
  }

  goToAppointment(id: number): void {
    this.router.navigate(['/appointments'], { queryParams: { appointmentId: id } });
  }

  goToPatients(): void {
    this.router.navigate(['/patients']);
  }

  goToPatient(id: number): void {
    this.router.navigate(['/patients', id]);
  }

  goToPrescriptions(): void {
    this.router.navigate(['/prescriptions']);
  }

  onPatientsSansDossierPageChange(page: number): void {
    this.patientsSansDossierPage.set(page);
  }

  onPatientsSansDossierSizeChange(size: number): void {
    this.patientsSansDossierSize.set(size);
    this.patientsSansDossierPage.set(0);
  }
}
