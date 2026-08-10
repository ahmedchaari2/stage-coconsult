import { Component, computed, inject, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslocoModule } from '@jsverse/transloco';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import { ClockCircleOutline, CheckCircleOutline, CheckCircleFill, CloseCircleOutline } from '@ant-design/icons-angular/icons';
import { AppointmentStatut } from 'src/app/core/models/appointment.model';
import { appointmentStatutBadgeClass, appointmentStatutIcon } from 'src/app/core/utils/appointment-statut';

@Component({
  selector: 'app-appointment-statut-badge',
  standalone: true,
  imports: [CommonModule, IconDirective, TranslocoModule],
  templateUrl: './appointment-statut-badge.html'
})
export class AppointmentStatutBadgeComponent {
  statut = input.required<AppointmentStatut>();

  readonly badgeClass = computed(() => appointmentStatutBadgeClass(this.statut()));
  readonly icon = computed(() => appointmentStatutIcon(this.statut()));

  constructor() {
    inject(IconService).addIcon(ClockCircleOutline, CheckCircleOutline, CheckCircleFill, CloseCircleOutline);
  }
}
