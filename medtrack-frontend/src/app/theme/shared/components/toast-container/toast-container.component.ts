import { Component, inject } from '@angular/core';
import { NgbToastModule } from '@ng-bootstrap/ng-bootstrap';

import { NotificationService, ToastType } from 'src/app/core/services/notification.service';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [NgbToastModule],
  templateUrl: './toast-container.component.html'
})
export class ToastContainerComponent {
  private readonly notificationService = inject(NotificationService);

  readonly toasts = this.notificationService.toasts;

  remove(id: number): void {
    this.notificationService.remove(id);
  }

  toastCssClass(type: ToastType): string {
    switch (type) {
      case 'success':
        return 'align-items-center text-white bg-success border-0';
      case 'warning':
        return 'align-items-center text-dark bg-warning border-0';
      default:
        return 'align-items-center text-white bg-danger border-0';
    }
  }
}
