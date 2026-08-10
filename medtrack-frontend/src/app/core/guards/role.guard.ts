import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Restreint une route aux rôles listés dans `data: { roles: [...] }`. Pas de rôles définis
 * -> pas de restriction. Non connecté -> login, rôle non autorisé -> dashboard.
 */
export const RoleGuard: CanActivateFn = (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const currentUser = authService.getCurrentUser();

  if (!currentUser) {
    return router.createUrlTree(['/auth/login']);
  }

  const allowedRoles = route.data?.['roles'] as string[] | undefined;

  if (!allowedRoles || allowedRoles.length === 0) {
    return true;
  }

  const userRole = currentUser.role; // "ADMIN" ou "MEDECIN", sans préfixe
  const hasAccess = allowedRoles.includes(userRole);

  if (!hasAccess) {
    return router.createUrlTree(['/dashboard']);
  }

  return true;
};
