import { Component, inject, input } from '@angular/core';
import { TranslocoModule } from '@jsverse/transloco';
import { IconDirective, IconService } from '@ant-design/icons-angular';
import { CheckCircleOutline, StopOutline } from '@ant-design/icons-angular/icons';

@Component({
  selector: 'app-medecin-statut-badge',
  standalone: true,
  imports: [IconDirective, TranslocoModule],
  templateUrl: './medecin-statut-badge.html'
})
export class MedecinStatutBadgeComponent {
  actif = input.required<boolean>();

  constructor() {
    inject(IconService).addIcon(CheckCircleOutline, StopOutline);
  }
}
