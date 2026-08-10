import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Vérifie le signal `isAuthenticated()` d'AuthService (maj après login / restauration de
 * session / refresh) et redirige vers /auth/login sinon. Le cookie d'accès n'étant plus
 * lisible côté client, on ne peut plus tester un token en mémoire : on se fie à ce flag,
 * l'expiration étant de toute façon gérée par l'intercepteur (refresh sur 401).
 */
export const AuthGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/auth/login']);
};
