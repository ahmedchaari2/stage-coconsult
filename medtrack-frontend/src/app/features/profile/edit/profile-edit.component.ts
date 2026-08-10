import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

import { CardComponent } from 'src/app/theme/shared/components/card/card.component';
import { AuthService } from 'src/app/core/services/auth.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { ProfileAvatarComponent } from '../avatar/profile-avatar.component';

@Component({
  selector: 'app-profile-edit',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterModule, CardComponent, TranslocoModule, ProfileAvatarComponent],
  templateUrl: './profile-edit.component.html',
  styleUrl: './profile-edit.component.scss'
})
export class ProfileEditComponent {
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly notificationService = inject(NotificationService);
  private readonly translocoService = inject(TranslocoService);

  isSubmitting = signal(false);
  // Email déjà pris par un autre compte (409) : erreur inline dédiée, distincte des erreurs de validation classiques.
  emailConflict = signal(false);

  profileForm: FormGroup;

  isSubmittingPassword = signal(false);
  currentPasswordInvalid = signal(false);
  passwordForm: FormGroup;

  readonly activeSection = toSignal(this.route.fragment.pipe(map((fragment) => (fragment === 'security' ? 'security' : 'profile'))), {
    initialValue: 'profile'
  });

  constructor() {
    const user = this.authService.getCurrentUser();
    this.profileForm = this.fb.group({
      prenom: [user?.prenom ?? '', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      nom: [user?.nom ?? '', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      email: [user?.email ?? '', [Validators.required, Validators.email, Validators.maxLength(255)]],
      telephone: [user?.telephone ?? '', [Validators.maxLength(30), Validators.pattern(/^[+()0-9 .-]*$/)]],
      specialite: [user?.specialite ?? ''],
      numeroOrdre: [user?.numeroOrdre ?? '']
    });

    this.profileForm.get('email')!.valueChanges.subscribe(() => this.emailConflict.set(false));

    this.passwordForm = this.fb.group({
      currentPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required, Validators.minLength(8)]]
    });

    this.passwordForm.get('currentPassword')!.valueChanges.subscribe(() => this.currentPasswordInvalid.set(false));
  }

  hasError(fieldName: string): boolean {
    const control = this.profileForm.get(fieldName);
    return !!control && control.touched && control.invalid;
  }

  getErrorMessage(fieldName: string): string {
    const control = this.profileForm.get(fieldName);
    if (!control?.errors || !control.touched) return '';
    if (control.errors['required']) return this.translocoService.translate('profile.validation.required');
    if (control.errors['minlength'])
      return this.translocoService.translate('profile.validation.minLength', { count: control.errors['minlength'].requiredLength });
    if (control.errors['maxlength'])
      return this.translocoService.translate('profile.validation.maxLength', { count: control.errors['maxlength'].requiredLength });
    if (control.errors['email']) return this.translocoService.translate('profile.validation.email');
    return this.translocoService.translate('profile.validation.invalid');
  }

  onSubmit(): void {
    this.emailConflict.set(false);

    if (this.profileForm.invalid) {
      Object.keys(this.profileForm.controls).forEach((key) => {
        this.profileForm.get(key)?.markAsTouched();
      });
      return;
    }

    this.isSubmitting.set(true);

    // Si l'email change, le backend résout l'utilisateur par email et l'access token courant devient caduc,
    this.authService
      .updateOwnProfile({
        nom: this.profileForm.value.nom?.trim(),
        prenom: this.profileForm.value.prenom?.trim(),
        email: this.profileForm.value.email?.trim(),
        telephone: this.profileForm.value.telephone?.trim() ?? '',
        specialite: this.profileForm.value.specialite?.trim() ?? '',
        numeroOrdre: this.profileForm.value.numeroOrdre?.trim() ?? ''
      })
      .subscribe({
        next: () => {
          this.isSubmitting.set(false);
          this.notificationService.showSuccess(this.translocoService.translate('profile.edit.success'));
          this.router.navigate(['/profile/view']);
        },
        error: (err) => {
          this.isSubmitting.set(false);
          if (err.status === 409) {
            this.emailConflict.set(true);
            return;
          }
          this.notificationService.showError(
            extractBackendErrorMessage(err) ??
              (err.status === 401
                ? this.translocoService.translate('profile.edit.unauthorized')
                : this.translocoService.translate('profile.edit.error'))
          );
        }
      });
  }

  onCancel(): void {
    this.router.navigate(['/profile/view']);
  }

  get isMedecin(): boolean {
    return this.authService.getCurrentUser()?.role === 'MEDECIN';
  }

  hasPasswordError(fieldName: string): boolean {
    const control = this.passwordForm.get(fieldName);
    return !!control && control.touched && control.invalid;
  }

  isNewPasswordMismatch(): boolean {
    const { newPassword, confirmPassword } = this.passwordForm.value;
    return !!confirmPassword && newPassword !== confirmPassword;
  }

  onSubmitPassword(): void {
    this.currentPasswordInvalid.set(false);

    if (this.passwordForm.invalid || this.isNewPasswordMismatch()) {
      Object.keys(this.passwordForm.controls).forEach((key) => {
        this.passwordForm.get(key)?.markAsTouched();
      });
      return;
    }

    this.isSubmittingPassword.set(true);

    this.authService
      .changePassword({
        currentPassword: this.passwordForm.value.currentPassword,
        newPassword: this.passwordForm.value.newPassword
      })
      .subscribe({
        next: (response) => {
          this.isSubmittingPassword.set(false);
          this.passwordForm.reset();
          this.notificationService.showSuccess(
            response.sessionsRevoked
              ? this.translocoService.translate('profile.security.successSessionsRevoked')
              : this.translocoService.translate('profile.security.success')
          );
        },
        error: (err) => {
          this.isSubmittingPassword.set(false);
          if (err.status === 400 || err.status === 401) {
            this.currentPasswordInvalid.set(true);
            return;
          }
          this.notificationService.showError(extractBackendErrorMessage(err) ?? this.translocoService.translate('profile.security.error'));
        }
      });
  }
}
