import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslocoModule } from '@jsverse/transloco';
import { AuthService } from 'src/app/core/services/auth.service';
import { MedecinDashboardComponent } from './medecin/medecin-dashboard.component';
import { AdminDashboardComponent } from './admin/admin-dashboard.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, TranslocoModule, MedecinDashboardComponent, AdminDashboardComponent],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent {
  private readonly authService = inject(AuthService);

  readonly isAdmin = computed(() => this.authService.currentUser()?.role === 'ADMIN');
  readonly isMedecin = computed(() => this.authService.currentUser()?.role === 'MEDECIN');
}
