import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { AdminLayout } from './theme/layouts/admin-layout/admin-layout.component';
import { GuestLayoutComponent } from './theme/layouts/guest-layout/guest-layout.component';
import { AuthGuard } from './core/guards/auth.guard';
import { RoleGuard } from './core/guards/role.guard';
import { HomeGuard } from './core/guards/home.guard';

const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [HomeGuard],
    children: []
  },
  {
    path: 'dashboard',
    component: AdminLayout,
    canActivate: [AuthGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./features/dashboard/dashboard.component').then((c) => c.DashboardComponent)
      }
    ]
  },
  {
    path: 'patients',
    component: AdminLayout,
    canActivate: [AuthGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./features/patient/list/patient-list.component').then((c) => c.PatientListComponent)
      },
      {
        path: ':id',
        loadComponent: () => import('./features/patient/detail/patient-detail.component').then((c) => c.PatientDetailComponent)
      }
    ]
  },
  {
    path: 'appointments',
    component: AdminLayout,
    canActivate: [AuthGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./features/appointment/list/appointment-list.component').then((c) => c.AppointmentListComponent)
      }
    ]
  },
  {
    path: 'prescriptions',
    component: AdminLayout,
    canActivate: [AuthGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./features/prescription/list/prescription-list.component').then((c) => c.PrescriptionListComponent)
      }
    ]
  },
  {
    path: 'profile',
    component: AdminLayout,
    canActivate: [AuthGuard],
    children: [
      {
        path: 'view',
        loadComponent: () => import('./features/profile/view/profile-view.component').then((c) => c.ProfileViewComponent)
      },
      {
        path: 'edit',
        loadComponent: () => import('./features/profile/edit/profile-edit.component').then((c) => c.ProfileEditComponent)
      },
      {
        path: '',
        redirectTo: 'view',
        pathMatch: 'full'
      }
    ]
  },
  {
    path: 'medecins',
    component: AdminLayout,
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN'] },
    children: [
      {
        path: '',
        loadComponent: () => import('./features/medecin/gestion/gestion').then((c) => c.Gestion)
      },
      {
        path: ':id',
        loadComponent: () => import('./features/medecin/detail/medecin-detail').then((c) => c.MedecinDetail)
      }
    ]
  },
  {
    path: 'journal-acces',
    component: AdminLayout,
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN'] },
    children: [
      {
        path: '',
        loadComponent: () => import('./features/acces-log/list/acces-log-list.component').then((c) => c.AccesLogListComponent)
      }
    ]
  },
  {
    path: 'auth',
    component: GuestLayoutComponent,
    children: [
      {
        path: 'login',
        loadComponent: () => import('./features/auth/login/login.component').then((c) => c.LoginComponent)
      },
      {
        path: 'register',
        redirectTo: 'login',
        pathMatch: 'full'
      },
      {
        path: 'forgot-password',
        loadComponent: () => import('./features/auth/forgot-password/forgot-password.component').then((c) => c.ForgotPasswordComponent)
      },
      {
        path: 'reset-password',
        loadComponent: () => import('./features/auth/reset-password/reset-password.component').then((c) => c.ResetPasswordComponent)
      }
    ]
  },
  {
    path: 'login',
    redirectTo: '/auth/login',
    pathMatch: 'full'
  },
  {
    path: 'register',
    redirectTo: '/auth/login',
    pathMatch: 'full'
  },
  {
    // pas de AuthGuard ici : l'utilisateur n'a pas encore de compte actif
    path: 'invitation',
    component: GuestLayoutComponent,
    children: [
      {
        path: ':token',
        loadComponent: () => import('./features/invitation/activate/activate').then((c) => c.Activate)
      }
    ]
  },
  {
    path: '**',
    redirectTo: '/dashboard'
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}
