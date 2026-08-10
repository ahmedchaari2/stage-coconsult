import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from 'src/app/core/services/auth.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { LoginRequest } from 'src/app/core/models/user.model';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { AuthCardHeaderComponent } from 'src/app/theme/shared/components/auth-card-header/auth-card-header.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, TranslocoModule, AuthCardHeaderComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly notificationService = inject(NotificationService);
  private readonly translocoService = inject(TranslocoService);

  submitted = signal(false);
  showPassword = signal(false);
  loading = signal(false);
  successMessage = signal('');

  constructor() {
    if (this.route.snapshot.queryParamMap.get('activated') === 'true') {
      this.successMessage.set(this.translocoService.translate('auth.login.activatedMessage'));
    } else if (this.route.snapshot.queryParamMap.get('passwordReset') === 'true') {
      this.successMessage.set(this.translocoService.translate('auth.login.passwordResetSuccess'));
    }
  }

  loginModel: LoginRequest = {
    email: '',
    password: ''
  };

  onSubmit(form: NgForm): void {
    this.submitted.set(true);

    if (form.invalid) {
      return;
    }

    this.loading.set(true);

    this.authService.login(this.loginModel).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading.set(false);
        // Message renvoyé par le backend sous la clé "error" (ex. compte désactivé -> 403)
        const backendMsg = extractBackendErrorMessage(err);
        if (backendMsg) {
          this.notificationService.showError(backendMsg);
          return;
        }
        // 403 sans message backend : peut être un compte désactivé ou un rejet CSRF (juste après logout), impossible à distinguer -> message neutre
        this.notificationService.showError(
          err.status === 403
            ? this.translocoService.translate('auth.login.accessDenied')
            : err.status === 401
              ? 'Email ou mot de passe invalide.'
              : 'Une erreur est survenue lors de la connexion.'
        );
      }
    });
  }

  togglePasswordVisibility(): void {
    this.showPassword.set(!this.showPassword());
  }
}
