import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { NgApexchartsModule, ApexOptions } from 'ng-apexcharts';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import {
  CalendarOutline,
  TeamOutline,
  MedicineBoxOutline,
  WarningOutline,
  ReloadOutline,
  DashboardOutline,
  EyeOutline,
  CloseCircleOutline,
  ClockCircleOutline
} from '@ant-design/icons-angular/icons';
import { CardComponent } from 'src/app/theme/shared/components/card/card.component';
import { AppointmentStatutBadgeComponent } from 'src/app/theme/shared/components/appointment-statut-badge/appointment-statut-badge';
import { DashboardService } from 'src/app/core/services/dashboard.service';
import { AdminDashboard, AujourdhuiResponse, CompteurFiltrable } from 'src/app/core/models/dashboard.model';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { TimeFormatPipe } from 'src/app/core/pipes/time-format.pipe';
import { Pagination } from 'src/app/theme/shared/components/pagination/pagination';

type Periode = '7j' | '30j' | 'mois';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    TranslocoModule,
    RouterLink,
    IconDirective,
    CardComponent,
    AppointmentStatutBadgeComponent,
    NgApexchartsModule,
    TimeFormatPipe,
    Pagination
  ],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: '../dashboard.shared.scss'
})
export class AdminDashboardComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly translocoService = inject(TranslocoService);
  private readonly iconService = inject(IconService);
  private readonly router = inject(Router);

  data = signal<AdminDashboard | null>(null);
  isLoading = signal(true);
  error = signal<string | null>(null);

  aujourdhui = signal<AujourdhuiResponse | null>(null);
  aujourdhuiLoading = signal(true);

  readonly dashboardPageSizeOptions = [5, 10, 25];
  aujourdhuiPage = signal(0);
  aujourdhuiSize = signal(5);
  activitePage = signal(0);
  activiteSize = signal(5);
  showAllFreeSlots = signal(false);

  readonly aujourdhuiItems = computed(() =>
    this.pageItems(this.aujourdhui()?.rendezVous ?? [], this.aujourdhuiPage(), this.aujourdhuiSize())
  );
  readonly aujourdhuiTotalPages = computed(() => this.pageCount(this.aujourdhui()?.rendezVous.length ?? 0, this.aujourdhuiSize()));
  readonly freeSlotItems = computed(() => {
    const items = this.aujourdhui()?.prochainesPlagesLibres ?? [];
    return this.showAllFreeSlots() ? items : items.slice(0, 4);
  });
  readonly hiddenFreeSlotCount = computed(() =>
    Math.max((this.aujourdhui()?.prochainesPlagesLibres.length ?? 0) - this.freeSlotItems().length, 0)
  );
  readonly activiteItems = computed(() =>
    this.pageItems(this.data()?.activiteParMedecin.items ?? [], this.activitePage(), this.activiteSize())
  );
  readonly activiteTotalPages = computed(() => this.pageCount(this.data()?.activiteParMedecin.items.length ?? 0, this.activiteSize()));

  periode = signal<Periode>('mois');

  constructor() {
    this.iconService.addIcon(
      CalendarOutline,
      TeamOutline,
      MedicineBoxOutline,
      WarningOutline,
      ReloadOutline,
      DashboardOutline,
      EyeOutline,
      CloseCircleOutline,
      ClockCircleOutline
    );
  }

  ngOnInit(): void {
    this.load();
    this.loadAujourdhui();
  }

  toggleFreeSlots(): void {
    this.showAllFreeSlots.update((value) => !value);
  }

  load(): void {
    this.isLoading.set(true);
    this.error.set(null);
    this.dashboardService.getAdminDashboard(this.periode()).subscribe({
      next: (data) => {
        this.data.set(data);
        this.activitePage.set(0);
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

  loadAujourdhui(): void {
    this.aujourdhuiLoading.set(true);
    this.dashboardService.getAujourdhui().subscribe({
      next: (data) => {
        this.aujourdhui.set(data);
        this.aujourdhuiPage.set(0);
        this.showAllFreeSlots.set(false);
        this.aujourdhuiLoading.set(false);
      },
      error: () => {
        this.aujourdhui.set({ disponible: false, rendezVous: [], aConfirmer: 0, annulesAujourdhui: 0, prochainesPlagesLibres: [] });
        this.aujourdhuiLoading.set(false);
      }
    });
  }

  onPeriodeChange(value: Periode): void {
    if (this.periode() === value) return;
    this.periode.set(value);
    this.load();
  }

  onAujourdhuiPageChange(page: number): void {
    this.aujourdhuiPage.set(page);
  }

  onAujourdhuiSizeChange(size: number): void {
    this.aujourdhuiSize.set(size);
    this.aujourdhuiPage.set(0);
  }

  onActivitePageChange(page: number): void {
    this.activitePage.set(page);
  }

  onActiviteSizeChange(size: number): void {
    this.activiteSize.set(size);
    this.activitePage.set(0);
  }

  readonly rdv7Chart = computed<Partial<ApexOptions>>(() => {
    const items = this.data()?.rendezVous7Jours?.items ?? [];
    return this.barChart(
      items.map((i) => this.shortDate(i.date)),
      items.map((i) => i.total),
      this.themeColor('--bs-primary')
    );
  });

  readonly rdv7Empty = computed(() => this.allZero(this.data()?.rendezVous7Jours?.items?.map((i) => i.total)));

  readonly annulationsChart = computed<Partial<ApexOptions>>(() => {
    const items = this.data()?.annulationsParSemaine?.items ?? [];
    return this.barChart(
      items.map((i) => i.semaine),
      items.map((i) => i.total),
      this.themeColor('--bs-danger')
    );
  });

  readonly nouveauxChart = computed<Partial<ApexOptions>>(() => {
    const items = this.data()?.patients?.nouveauxParMois ?? [];
    return this.barChart(
      items.map((i) => i.mois),
      items.map((i) => i.total),
      this.themeColor('--bs-success')
    );
  });

  readonly nouveauxEmpty = computed(() => this.allZero(this.data()?.patients?.nouveauxParMois?.map((i) => i.total)));

  readonly ageChart = computed<Partial<ApexOptions>>(() => {
    const map = this.data()?.patients?.parTrancheAge ?? {};
    const keys = Object.keys(map);
    return this.barChart(
      keys,
      keys.map((k) => map[k]),
      this.themeColor('--bs-info'),
      true
    );
  });

  readonly ageEmpty = computed(() => !this.hasData(this.data()?.patients?.parTrancheAge));

  readonly sexeEmpty = computed(() => !this.hasData(this.sexeData()));

  readonly sexeChart = computed<Partial<ApexOptions>>(() => {
    const map = this.sexeData();
    const keys = Object.keys(map);
    return {
      chart: { type: 'donut', height: 240, background: 'transparent' },
      series: keys.map((k) => map[k]),
      labels: keys.map((k) => this.translocoService.translate('dashboard.admin.patients.sexe.' + k)),
      colors: [this.themeColor('--bs-primary'), this.themeColor('--bs-info'), this.themeColor('--bs-secondary')],
      legend: { position: 'bottom' },
      dataLabels: { enabled: false },
      stroke: { width: 0 }
    };
  });

  private sexeData(): Record<string, number> {
    const map = this.data()?.patients?.parSexe ?? {};
    return Object.fromEntries(Object.entries(map).filter(([key]) => key !== 'AUTRE'));
  }

  readonly occupationChart = computed<Partial<ApexOptions>>(() => {
    const pct = Math.round((this.data()?.tauxOccupation?.taux ?? 0) * 1000) / 10; // 1 décimale
    return {
      chart: { type: 'radialBar', height: 240, background: 'transparent' },
      series: [pct],
      labels: [this.translocoService.translate('dashboard.admin.occupation.label')],
      colors: [this.themeColor('--bs-primary')],
      plotOptions: { radialBar: { hollow: { size: '60%' } } }
    };
  });

  readonly occupationEmpty = computed(() => (this.data()?.tauxOccupation?.creneauxTheoriques ?? 0) === 0);

  readonly occupationLabel = computed(() => {
    const occ = this.data()?.tauxOccupation;
    if (!occ) return '';
    return this.translocoService.translate('dashboard.admin.occupation.detail', {
      occupes: occ.rendezVousNonAnnules,
      total: occ.creneauxTheoriques
    });
  });

  private barChart(categories: string[], data: number[], color: string, rotateLabels = false): Partial<ApexOptions> {
    return {
      chart: { type: 'bar', height: 240, background: 'transparent', toolbar: { show: false } },
      series: [{ name: '', data }],
      colors: [color],
      plotOptions: { bar: { borderRadius: 4, columnWidth: '55%' } },
      dataLabels: { enabled: false },
      xaxis: {
        categories,
        labels: { style: { colors: this.themeColor('--bs-secondary-color') }, rotate: rotateLabels ? -45 : 0, trim: false }
      },
      yaxis: { labels: { style: { colors: this.themeColor('--bs-secondary-color') } } },
      grid: { borderColor: this.themeColor('--bs-border-color') },
      legend: { show: false }
    };
  }

  private themeColor(varName: string): string {
    const value = getComputedStyle(document.documentElement).getPropertyValue(varName).trim();
    return value || '#000000';
  }

  private hasData(map: Record<string, number> | undefined): boolean {
    return !!map && Object.values(map).some((v) => v > 0);
  }

  private allZero(values: number[] | undefined): boolean {
    return !values || values.length === 0 || values.every((v) => v === 0);
  }

  private pageItems<T>(items: T[], page: number, size: number): T[] {
    const start = page * size;
    return items.slice(start, start + size);
  }

  private pageCount(total: number, size: number): number {
    return Math.ceil(total / size);
  }

  goToAppointments(): void {
    this.router.navigate(['/appointments']);
  }

  goToPatients(filtre?: CompteurFiltrable['filtre']): void {
    this.router.navigate(['/patients'], filtre && Object.keys(filtre).length ? { queryParams: filtre } : {});
  }

  goToMedecins(filtre?: CompteurFiltrable['filtre']): void {
    this.router.navigate(['/medecins'], filtre && Object.keys(filtre).length ? { queryParams: filtre } : {});
  }

  goToAppointment(id: number): void {
    this.router.navigate(['/appointments'], { queryParams: { appointmentId: id } });
  }

  private shortDate(value: string): string {
    const [, m, d] = value.slice(0, 10).split('-');
    return `${d}/${m}`;
  }
}
