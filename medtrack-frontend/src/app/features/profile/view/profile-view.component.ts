import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

import { CardComponent } from 'src/app/theme/shared/components/card/card.component';
import { AuthService } from 'src/app/core/services/auth.service';
import { TranslocoModule } from '@jsverse/transloco';
import { ProfileAvatarComponent } from '../avatar/profile-avatar.component';
import { EmailLinkComponent } from 'src/app/theme/shared/components/email-link/email-link';
import { PhoneLinkComponent } from 'src/app/theme/shared/components/phone-link/phone-link';
import { DateFormatPipe } from 'src/app/core/pipes/date-format.pipe';
import { MedecinStatutBadgeComponent } from 'src/app/theme/shared/components/medecin-statut-badge/medecin-statut-badge';

@Component({
  selector: 'app-profile-view',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    CardComponent,
    TranslocoModule,
    ProfileAvatarComponent,
    EmailLinkComponent,
    PhoneLinkComponent,
    DateFormatPipe,
    MedecinStatutBadgeComponent
  ],
  templateUrl: './profile-view.component.html',
  styleUrl: './profile-view.component.scss'
})
export class ProfileViewComponent {
  private readonly authService = inject(AuthService);

  // Signal réactif : se met à jour après restauration de session (F5) sans rechargement du composant.
  readonly currentUser = this.authService.currentUser;
}
