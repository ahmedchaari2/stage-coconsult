import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuthService } from 'src/app/core/services/auth.service';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { AuthCardHeaderComponent } from 'src/app/theme/shared/components/auth-card-header/auth-card-header.component';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, TranslocoModule, AuthCardHeaderComponent],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss'
})
export class ForgotPasswordComponent {
  private readonly authService = inject(AuthService);
  private readonly translocoService = inject(TranslocoService);

  submitted = signal(false);
  loading = signal(false);
  // Une fois à true on reste sur le message générique, jamais de retour au form (pour ne pas révéler si l'email existe).
  sent = signal(false);
  errorMessage = signal('');

  email = '';

  // Succès HTTP -> toujours le message générique, jamais "cet email n'existe pas". Une vraie erreur serveur reste affichée normalement.
  onSubmit(form: NgForm): void {
    this.submitted.set(true);
    this.errorMessage.set('');

    if (form.invalid) {
      return;
    }

    this.loading.set(true);

    this.authService.forgotPassword(this.email.trim()).subscribe({
      next: () => {
        this.loading.set(false);
        this.sent.set(true);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(extractBackendErrorMessage(err) ?? this.translocoService.translate('auth.forgotPassword.error'));
      }
    });
  }
}
