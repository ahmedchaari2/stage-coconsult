import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';

import { InvitationService } from 'src/app/core/services/invitation.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { ActivationRequest, InvitationValidation } from 'src/app/core/models/invitation.model';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { AuthCardHeaderComponent } from 'src/app/theme/shared/components/auth-card-header/auth-card-header.component';

@Component({
  selector: 'app-activate',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, TranslocoModule, AuthCardHeaderComponent],
  templateUrl: './activate.html',
  styleUrl: './activate.scss'
})
export class Activate implements OnInit {
  private readonly invitationService = inject(InvitationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notificationService = inject(NotificationService);
  private readonly translocoService = inject(TranslocoService);

  tokenStatus = signal<'loading' | 'valid' | 'error'>('loading');
  tokenError = signal<string>('');
  // Cas expiré (410) : "retour à la connexion" ne suffit pas, il faut dire de redemander une invitation à un administrateur.
  tokenExpired = signal(false);
  invitation = signal<InvitationValidation | null>(null);

  submitted = signal(false);
  loading = signal(false);
  showPassword = signal(false);
  showConfirmPassword = signal(false);
  confirmPassword = '';

  activationModel: ActivationRequest = {
    password: '',
    nom: '',
    prenom: '',
    specialite: '',
    numeroOrdre: '',
    telephone: ''
  };

  ngOnInit(): void {
    const token = this.route.snapshot.paramMap.get('token');
    if (!token) {
      this.tokenStatus.set('error');
      this.tokenError.set(this.translocoService.translate('invitation.activate.errors.invalidLink'));
      return;
    }
    this.validateToken(token);
  }

  // Le backend renvoie déjà un motif précis (introuvable/expirée/déjà utilisée) : on l'affiche tel quel plutôt que de le retraduire nous-mêmes.
  private validateToken(token: string): void {
    this.tokenStatus.set('loading');

    this.invitationService.getInvitationByToken(token).subscribe({
      next: (data) => {
        this.invitation.set(data);
        this.tokenStatus.set('valid');
      },
      error: (err) => {
        this.tokenStatus.set('error');
        this.tokenExpired.set(err.status === 410);
        this.tokenError.set(
          extractBackendErrorMessage(err) ?? this.translocoService.translate('invitation.activate.errors.validateFailed')
        );
      }
    });
  }

  onSubmit(form: NgForm): void {
    const token = this.route.snapshot.paramMap.get('token');

    this.submitted.set(true);

    if (form.invalid || this.isPasswordMismatch()) {
      return;
    }
    if (!token) {
      return;
    }

    this.loading.set(true);

    const payload: ActivationRequest = {
      ...this.activationModel,
      specialite: this.activationModel.specialite?.trim() || null,
      numeroOrdre: this.activationModel.numeroOrdre?.trim() || null,
      telephone: this.activationModel.telephone?.trim() || null
    };

    this.invitationService.activateInvitation(token, payload).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/auth/login'], { queryParams: { activated: 'true' } });
      },
      error: (err) => {
        this.loading.set(false);
        this.notificationService.showError(
          extractBackendErrorMessage(err) ?? this.translocoService.translate('invitation.activate.errors.activateFailed')
        );
      }
    });
  }

  togglePasswordVisibility(): void {
    this.showPassword.set(!this.showPassword());
  }

  toggleConfirmPasswordVisibility(): void {
    this.showConfirmPassword.set(!this.showConfirmPassword());
  }

  isPasswordMismatch(): boolean {
    return this.activationModel.password !== this.confirmPassword;
  }
}
