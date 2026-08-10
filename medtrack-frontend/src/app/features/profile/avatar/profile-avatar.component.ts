import { Component, ElementRef, TemplateRef, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AuthService } from 'src/app/core/services/auth.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { extractBackendErrorMessage } from 'src/app/core/utils/http-error';
import { IconService } from '@ant-design/icons-angular';
import { CameraOutline, DeleteOutline } from '@ant-design/icons-angular/icons';

const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 Mo
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const DEFAULT_AVATAR = 'assets/images/user/avatar-default.svg';

@Component({
  selector: 'app-profile-avatar',
  standalone: true,
  imports: [CommonModule, TranslocoModule],
  templateUrl: './profile-avatar.component.html',
  styleUrl: './profile-avatar.component.scss'
})
export class ProfileAvatarComponent {
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly translocoService = inject(TranslocoService);
  private readonly modalService = inject(NgbModal);
  private readonly iconService = inject(IconService);

  @ViewChild('fileInput') fileInputRef!: ElementRef<HTMLInputElement>;
  @ViewChild('removeConfirmModal') removeConfirmModal!: TemplateRef<unknown>;

  readonly currentUser = this.authService.currentUser;
  readonly defaultAvatar = DEFAULT_AVATAR;

  // Aperçu local avant envoi (data URL), distinct de photoUrl (issu du serveur)
  previewUrl = signal<string | null>(null);
  selectedFile = signal<File | null>(null);
  uploading = signal(false);
  removing = signal(false);
  clientError = signal('');

  constructor() {
    this.iconService.addIcon(...[CameraOutline, DeleteOutline]);
  }

  onAvatarClick(): void {
    this.fileInputRef.nativeElement.click();
  }

  onFileSelected(event: Event): void {
    this.clientError.set('');
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }

    if (!ALLOWED_TYPES.includes(file.type)) {
      this.clientError.set(this.translocoService.translate('profile.avatar.invalidType'));
      input.value = '';
      return;
    }
    if (file.size > MAX_FILE_SIZE_BYTES) {
      this.clientError.set(this.translocoService.translate('profile.avatar.invalidSize'));
      input.value = '';
      return;
    }

    this.selectedFile.set(file);
    const reader = new FileReader();
    reader.onload = () => this.previewUrl.set(reader.result as string);
    reader.readAsDataURL(file);
  }

  cancelPreview(): void {
    this.previewUrl.set(null);
    this.selectedFile.set(null);
    this.clientError.set('');
    if (this.fileInputRef) {
      this.fileInputRef.nativeElement.value = '';
    }
  }

  confirmUpload(): void {
    const file = this.selectedFile();
    if (!file) {
      return;
    }

    this.uploading.set(true);

    this.authService.uploadProfilePhoto(file).subscribe({
      next: () => {
        this.uploading.set(false);
        this.cancelPreview();
        this.notificationService.showSuccess(this.translocoService.translate('profile.avatar.uploadSuccess'));
      },
      error: (err) => {
        this.uploading.set(false);
        this.notificationService.showError(
          extractBackendErrorMessage(err) ?? this.translocoService.translate('profile.avatar.uploadError')
        );
      }
    });
  }

  onRemoveClick(): void {
    this.modalService.open(this.removeConfirmModal, { centered: true, backdrop: 'static' }).result.then(
      (result) => {
        if (result === 'confirm') {
          this.removePhoto();
        }
      },
      () => {}
    );
  }

  private removePhoto(): void {
    this.removing.set(true);
    this.authService.deleteProfilePhoto().subscribe({
      next: () => {
        this.removing.set(false);
        this.notificationService.showSuccess(this.translocoService.translate('profile.avatar.removeSuccess'));
      },
      error: (err) => {
        this.removing.set(false);
        this.notificationService.showError(
          extractBackendErrorMessage(err) ?? this.translocoService.translate('profile.avatar.removeError')
        );
      }
    });
  }
}
