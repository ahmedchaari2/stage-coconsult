import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Route racine ('/') : redirige vers /dashboard si une session est active (restaurée par
 * bootstrapSession au démarrage), /auth/login sinon. Remplace un redirectTo statique vers
 * /login qui s'appliquait même en étant déjà connecté.
 */
export const HomeGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return router.createUrlTree([authService.isAuthenticated() ? '/dashboard' : '/auth/login']);
};
