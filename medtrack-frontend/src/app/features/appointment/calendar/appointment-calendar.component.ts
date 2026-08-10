import { Component, OnInit, computed, inject, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import {
  CalendarOutline,
  LeftOutline,
  RightOutline,
  ClockCircleOutline,
  CheckCircleOutline,
  CheckCircleFill,
  CloseCircleOutline
} from '@ant-design/icons-angular/icons';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

import { AppointmentService } from 'src/app/core/services/appointment.service';
import { Appointment } from 'src/app/core/models/appointment.model';
import { appointmentStatutBadgeClass, appointmentStatutIcon } from 'src/app/core/utils/appointment-statut';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { TimeFormatPipe } from 'src/app/core/pipes/time-format.pipe';

interface CalendarDay {
  date: Date;
  key: string;
  inCurrentMonth: boolean;
  isToday: boolean;
  isSchedulingDisabled: boolean;
  visibleAppointments: Appointment[];
  additionalAppointmentCount: number;
}

@Component({
  selector: 'app-appointment-calendar',
  standalone: true,
  imports: [CommonModule, TranslocoModule, IconDirective, NgbTooltip, TimeFormatPipe],
  templateUrl: './appointment-calendar.component.html',
  styleUrl: './appointment-calendar.component.scss'
})
export class AppointmentCalendarComponent implements OnInit {
  private readonly appointmentService = inject(AppointmentService);
  private readonly translocoService = inject(TranslocoService);
  private readonly iconService = inject(IconService);

  readonly dayClick = output<string>();
  readonly dayAppointmentsClick = output<string>();
  readonly appointmentClick = output<Appointment>();

  private readonly maxVisibleAppointmentsPerDay = 3;

  private readonly today = new Date();
  currentYear = signal(this.today.getFullYear());
  currentMonth = signal(this.today.getMonth() + 1); // 1-12

  appointments = signal<Appointment[]>([]);
  isLoading = signal(true);
  error = signal<string | null>(null);

  readonly weekdayKeys = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'];

  monthLabel = computed(() => {
    const locale = this.translocoService.getActiveLang() === 'en' ? 'en-US' : 'fr-FR';
    const date = new Date(this.currentYear(), this.currentMonth() - 1, 1);
    const label = new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(date);
    return label.charAt(0).toUpperCase() + label.slice(1);
  });

  private appointmentsByDay = computed(() => {
    const map = new Map<string, Appointment[]>();
    for (const appt of this.appointments()) {
      const key = appt.dateHeure.slice(0, 10);
      const list = map.get(key);
      if (list) {
        list.push(appt);
      } else {
        map.set(key, [appt]);
      }
    }
    return map;
  });

  weeks = computed<CalendarDay[][]>(() => {
    const year = this.currentYear();
    const month = this.currentMonth();
    const firstOfMonth = new Date(year, month - 1, 1);
    const startOffset = (firstOfMonth.getDay() + 6) % 7;
    const gridStart = new Date(year, month - 1, 1 - startOffset);
    const byDay = this.appointmentsByDay();

    const days: CalendarDay[] = [];
    for (let i = 0; i < 42; i++) {
      const date = new Date(gridStart);
      date.setDate(gridStart.getDate() + i);
      const key = this.dateKey(date);
      const appointments = (byDay.get(key) ?? []).sort((a, b) => a.dateHeure.localeCompare(b.dateHeure));
      days.push({
        date,
        key,
        inCurrentMonth: date.getMonth() === month - 1,
        isToday: this.dateKey(date) === this.dateKey(this.today),
        isSchedulingDisabled: date.getDay() === 0,
        visibleAppointments: appointments.slice(0, this.maxVisibleAppointmentsPerDay),
        additionalAppointmentCount: Math.max(0, appointments.length - this.maxVisibleAppointmentsPerDay)
      });
    }

    const weeks: CalendarDay[][] = [];
    for (let i = 0; i < 42; i += 7) {
      weeks.push(days.slice(i, i + 7));
    }
    return weeks;
  });

  constructor() {
    this.iconService.addIcon(
      CalendarOutline,
      LeftOutline,
      RightOutline,
      ClockCircleOutline,
      CheckCircleOutline,
      CheckCircleFill,
      CloseCircleOutline
    );
  }

  ngOnInit(): void {
    this.loadMonth();
  }

  private dateKey(date: Date): string {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  }

  loadMonth(): void {
    this.isLoading.set(true);
    this.error.set(null);
    const pad = (n: number) => String(n).padStart(2, '0');
    const month = `${this.currentYear()}-${pad(this.currentMonth())}`;

    this.appointmentService.getCalendar(month).subscribe({
      next: (data) => {
        this.appointments.set(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        // Priorité au message du backend (ou timeout de l'intercepteur), sinon message générique.
        this.error.set(extractBackendErrorMessage(err) ?? this.translocoService.translate('appointment.errors.loadFailed'));
        this.isLoading.set(false);
      }
    });
  }

  previousMonth(): void {
    let month = this.currentMonth() - 1;
    let year = this.currentYear();
    if (month < 1) {
      month = 12;
      year -= 1;
    }
    this.currentMonth.set(month);
    this.currentYear.set(year);
    this.loadMonth();
  }

  nextMonth(): void {
    let month = this.currentMonth() + 1;
    let year = this.currentYear();
    if (month > 12) {
      month = 1;
      year += 1;
    }
    this.currentMonth.set(month);
    this.currentYear.set(year);
    this.loadMonth();
  }

  goToday(): void {
    this.currentYear.set(this.today.getFullYear());
    this.currentMonth.set(this.today.getMonth() + 1);
    this.loadMonth();
  }

  onDayClick(day: CalendarDay): void {
    if (day.isSchedulingDisabled) {
      return;
    }
    this.dayClick.emit(day.key);
  }

  onAppointmentClick(event: Event, appt: Appointment): void {
    event.stopPropagation();
    this.appointmentClick.emit(appt);
  }

  onMoreClick(event: Event, day: CalendarDay): void {
    event.stopPropagation();
    this.dayAppointmentsClick.emit(day.key);
  }

  readonly statutBadgeClass = appointmentStatutBadgeClass;
  readonly statutIcon = appointmentStatutIcon;
}
