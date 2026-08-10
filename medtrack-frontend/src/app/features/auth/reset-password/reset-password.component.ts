import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from 'src/app/core/services/auth.service';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { AuthCardHeaderComponent } from 'src/app/theme/shared/components/auth-card-header/auth-card-header.component';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, TranslocoModule, AuthCardHeaderComponent],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss'
})
export class ResetPasswordComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly translocoService = inject(TranslocoService);

  // null si absent de l'URL (lien copié/tronqué) : dans ce cas le formulaire ne s'affiche pas.
  token = signal<string | null>(null);

  submitted = signal(false);
  loading = signal(false);
  showPassword = signal(false);
  showConfirmPassword = signal(false);
  errorMessage = signal('');

  newPassword = '';
  confirmPassword = '';

  ngOnInit(): void {
    this.token.set(this.route.snapshot.queryParamMap.get('token'));
  }

  // Token invalide/expiré -> on affiche le message backend précis pour que l'utilisateur redemande un lien plutôt que de réessayer le même mot de passe.
  onSubmit(form: NgForm): void {
    this.submitted.set(true);
    this.errorMessage.set('');

    if (form.invalid || this.isPasswordMismatch()) {
      return;
    }

    const token = this.token();
    if (!token) {
      return;
    }

    this.loading.set(true);

    this.authService.resetPassword({ token, newPassword: this.newPassword }).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/auth/login'], { queryParams: { passwordReset: 'true' } });
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(extractBackendErrorMessage(err) ?? this.translocoService.translate('auth.resetPassword.error'));
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
    return this.newPassword !== this.confirmPassword;
  }
}
