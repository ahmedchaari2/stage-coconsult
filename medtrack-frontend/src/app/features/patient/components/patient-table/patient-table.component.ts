import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import { EditOutline, EyeOutline, FileTextOutline, InboxOutline, RollbackOutline } from '@ant-design/icons-angular/icons';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoModule } from '@jsverse/transloco';
import { RouterLink } from '@angular/router';

import { Patient } from 'src/app/core/models/patient.model';
import { DateFormatPipe } from 'src/app/core/pipes/date-format.pipe';
import { EmailLinkComponent } from 'src/app/theme/shared/components/email-link/email-link';
import { ListErrorStateComponent } from 'src/app/theme/shared/components/list-error-state/list-error-state.component';
import { Pagination } from 'src/app/theme/shared/components/pagination/pagination';
import { PhoneLinkComponent } from 'src/app/theme/shared/components/phone-link/phone-link';
import { SpinnerComponent } from 'src/app/theme/shared/components/spinner/spinner.component';
import { Spinkit } from 'src/app/theme/shared/components/spinner/spinkits';
import { SortableColumn, SortState } from 'src/app/theme/shared/directives/sortable-column';

@Component({
  selector: 'app-patient-table',
  standalone: true,
  imports: [
    DateFormatPipe,
    EmailLinkComponent,
    IconDirective,
    ListErrorStateComponent,
    NgbTooltip,
    Pagination,
    PhoneLinkComponent,
    RouterLink,
    SortableColumn,
    SpinnerComponent,
    TranslocoModule
  ],
  templateUrl: './patient-table.component.html',
  styleUrl: './patient-table.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PatientTableComponent {
  patients = input.required<Patient[]>();
  isLoading = input(false);
  error = input<string | null>(null);
  searchTerm = input('');
  canRestoreArchived = input(false);
  isAdmin = input(false);
  medecinReferentLabel = input.required<(patient: Patient) => string>();

  page = input(0);
  totalPages = input(0);
  totalElements = input(0);
  size = input(10);
  sortState = input<SortState | null>(null);

  editRequested = output<Patient>();
  archiveToggleRequested = output<Patient>();
  retryRequested = output<void>();
  resetSearchRequested = output<void>();
  pageChange = output<number>();
  sizeChange = output<number>();
  sortChange = output<SortState>();

  readonly spinkit = Spinkit.skLine;

  constructor() {
    inject(IconService).addIcon(EditOutline, EyeOutline, FileTextOutline, InboxOutline, RollbackOutline);
  }
}
