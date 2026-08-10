import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import {
  CalendarOutline,
  CarryOutOutline,
  CheckOutline,
  DeleteOutline,
  EditOutline,
  EyeOutline,
  FileTextOutline,
  MedicineBoxOutline,
  MessageOutline,
  StopOutline
} from '@ant-design/icons-angular/icons';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoModule } from '@jsverse/transloco';
import { RouterLink } from '@angular/router';

import { Appointment, AppointmentStatut } from 'src/app/core/models/appointment.model';
import { DateTimeFormatPipe } from 'src/app/core/pipes/date-time-format.pipe';
import { AppointmentStatutBadgeComponent } from 'src/app/theme/shared/components/appointment-statut-badge/appointment-statut-badge';
import { ListErrorStateComponent } from 'src/app/theme/shared/components/list-error-state/list-error-state.component';
import { Pagination } from 'src/app/theme/shared/components/pagination/pagination';
import { SortableColumn, SortState } from 'src/app/theme/shared/directives/sortable-column';
import { SpinnerComponent } from 'src/app/theme/shared/components/spinner/spinner.component';
import { Spinkit } from 'src/app/theme/shared/components/spinner/spinkits';

export interface AppointmentStatusChange {
  appointment: Appointment;
  statut: AppointmentStatut;
}

@Component({
  selector: 'app-appointment-table',
  standalone: true,
  imports: [
    AppointmentStatutBadgeComponent,
    DateTimeFormatPipe,
    IconDirective,
    ListErrorStateComponent,
    NgbTooltip,
    Pagination,
    RouterLink,
    SortableColumn,
    SpinnerComponent,
    TranslocoModule
  ],
  templateUrl: './appointment-table.component.html',
  styleUrl: './appointment-table.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppointmentTableComponent {
  readonly spinkit = Spinkit.skLine;

  appointments = input.required<Appointment[]>();
  isLoading = input(false);
  error = input<string | null>(null);
  highlightedAppointmentId = input<number | null>(null);
  statusUpdatingId = input<number | null>(null);
  consultationLookupId = input<number | null>(null);
  consultationUnavailableIds = input<ReadonlySet<number>>(new Set<number>());
  canViewMedecin = input(false);
  canStartConsultation = input.required<(appointment: Appointment) => boolean>();

  page = input(0);
  totalPages = input(0);
  totalElements = input(0);
  size = input(10);
  sortState = input<SortState | null>(null);

  retryRequested = output<void>();
  viewRequested = output<Appointment>();
  editRequested = output<Appointment>();
  deleteRequested = output<Appointment>();
  statusChangeRequested = output<AppointmentStatusChange>();
  startConsultationRequested = output<Appointment>();
  viewConsultationRequested = output<Appointment>();
  pageChange = output<number>();
  sizeChange = output<number>();
  sortChange = output<SortState>();

  constructor() {
    inject(IconService).addIcon(
      CalendarOutline,
      CarryOutOutline,
      CheckOutline,
      DeleteOutline,
      EditOutline,
      EyeOutline,
      FileTextOutline,
      MedicineBoxOutline,
      MessageOutline,
      StopOutline
    );
  }

  requestStatusChange(appointment: Appointment, statut: AppointmentStatut): void {
    this.statusChangeRequested.emit({ appointment, statut });
  }
}
