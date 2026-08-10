import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpErrorResponse, HttpEvent, HttpContextToken } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, TimeoutError, catchError, switchMap, throwError, timeout } from 'rxjs';
import { TranslocoService } from '@jsverse/transloco';
import { AuthService, SKIP_AUTH } from '../services/auth.service';

/**
 * Délai maximal accordé à une requête avant abandon côté client. Calé juste au-dessus du pire
 * cas backend (3s + 3s de Feign en cascade sur prescription-service, 5s de timeout Gateway) :
 * assez large pour laisser la Gateway répondre son erreur elle-même, assez court pour qu'un
 * service muet n'immobilise jamais l'écran sur un spinner sans fin.
 */
const HTTP_TIMEOUT_MS = 7000;

/**
 * Permet à un appel précis de dépasser HTTP_TIMEOUT_MS (ex. le chat IA, dont le budget Gateway
 * va jusqu'à 22s côté ai-service). Absent -> comportement inchangé pour tout le reste de l'app.
 */
export const HTTP_TIMEOUT_OVERRIDE_MS = new HttpContextToken<number | null>(() => null);

/**
 * Marque une requête comme "déjà retentée après un 403 CSRF", posé sur le clone
 * envoyé par `retryWithFreshCsrfToken` pour ne rejouer qu'une seule fois — sans
 * ce garde-fou, un 403 persistant (ex. rôle insuffisant, compte désactivé)
 * bouclerait à l'infini.
 */
const CSRF_RETRIED = new HttpContextToken<boolean>(() => false);

/**
 * Intercepteur d'authentification, système 100 % cookies httpOnly (donc pas de header
 * Authorization: Bearer, le navigateur envoie les cookies tout seul). Il pose withCredentials
 * sur chaque requête, injecte X-XSRF-TOKEN sur les requêtes mutantes, retente un refresh
 * automatique sur 401 avant de rejouer la requête, et coupe toute requête qui traîne trop
 * longtemps (HTTP_TIMEOUT_MS) en 504 traduit pour rester dans le même format d'erreur partout.
 *
 * Le cas plus tordu : un 403 sur une requête mutante déclenche un seul retry avec relecture
 * fraîche du cookie XSRF-TOKEN (voir CSRF_RETRIED). Le backend régénère ce cookie à chaque
 * login/refresh/logout (toujours une nouvelle valeur, jamais de réutilisation), donc un cycle
 * 401 -> refresh -> retry en arrière-plan, ou une requête concurrente à un login/logout, peut
 * laisser partir un X-XSRF-TOKEN désynchronisé du cookie que le navigateur vient de renouveler.
 * Avant, il suffisait de recliquer pour que ça reparte (le cookie était relu) ; ce retry fait
 * pareil automatiquement.
 */
export const authInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn): Observable<HttpEvent<unknown>> => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const transloco = inject(TranslocoService);
  const skipAuth = req.context.get(SKIP_AUTH);

  // `withCredentials: true` est requis pour que les cookies httpOnly
  const authReq = req.clone({ withCredentials: true });
  const securedReq = withXsrfToken(authReq);

  return sendWithTimeout(securedReq, next, transloco).pipe(
    catchError((error) => {
      if (isUnauthorized(error) && !skipAuth) {
        return handleUnauthorized(req, securedReq, next, error, authService, router, transloco);
      }
      if (isRetriableCsrfFailure(error, securedReq)) {
        return retryWithFreshCsrfToken(req, next, transloco);
      }
      return throwError(() => error);
    })
  );
};

/**
 * Envoie la requête avec un plafond de temps. Le TimeoutError de RxJS est traduit en
 * HttpErrorResponse 504 : les écrans n'ont ainsi qu'un seul type d'erreur à gérer, et le
 * message part dans `error.error` là où extractBackendErrorMessage va déjà le chercher.
 * Appliqué à chaque envoi, y compris aux rejeux (refresh, CSRF), pour qu'aucune requête
 * ne puisse rester en vol indéfiniment.
 */
function sendWithTimeout(req: HttpRequest<unknown>, next: HttpHandlerFn, transloco: TranslocoService): Observable<HttpEvent<unknown>> {
  const timeoutMs = req.context.get(HTTP_TIMEOUT_OVERRIDE_MS) ?? HTTP_TIMEOUT_MS;
  return next(req).pipe(
    timeout(timeoutMs),
    catchError((error) => throwError(() => (error instanceof TimeoutError ? toTimeoutResponse(req, transloco) : error)))
  );
}

function toTimeoutResponse(req: HttpRequest<unknown>, transloco: TranslocoService): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 504,
    statusText: 'Gateway Timeout',
    url: req.urlWithParams,
    error: { error: transloco.translate('common.errors.timeout') }
  });
}

/** Vrai uniquement pour une réponse HTTP 401 (token expiré/invalide). */
function isUnauthorized(error: unknown): error is HttpErrorResponse {
  return error instanceof HttpErrorResponse && error.status === 401;
}

/**
 * Vrai pour un 403 sur une requête mutante pas encore rejouée (voir CSRF_RETRIED) :
 * seul cas où relire le cookie XSRF-TOKEN et renvoyer peut changer l'issue (un 403
 * pour rôle insuffisant ou compte désactivé échouera identiquement au retry, coût
 * d'un aller-retour réseau supplémentaire mais sans risque — le retry ne fait que
 * rejouer la même action déjà autorisée ou non, il n'élargit aucun droit).
 */
function isRetriableCsrfFailure(error: unknown, req: HttpRequest<unknown>): boolean {
  if (!(error instanceof HttpErrorResponse) || error.status !== 403) {
    return false;
  }
  if (req.context.get(CSRF_RETRIED)) {
    return false;
  }
  return ['POST', 'PUT', 'PATCH', 'DELETE'].includes(req.method.toUpperCase());
}

/** Rejoue la requête ORIGINALE (pas securedReq, pour repartir d'un header propre) avec une relecture fraîche du cookie XSRF-TOKEN. */
function retryWithFreshCsrfToken(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  transloco: TranslocoService
): Observable<HttpEvent<unknown>> {
  const retryReq = withXsrfToken(req.clone({ withCredentials: true, context: req.context.set(CSRF_RETRIED, true) }));
  return sendWithTimeout(retryReq, next, transloco);
}

/**
 * Lit le cookie `XSRF-TOKEN` (non-HttpOnly, donc lisible via document.cookie)
 * et l'injecte dans le header `X-XSRF-TOKEN` sur les requêtes modifiant
 * l'état. Les requêtes en lecture (GET…) n'en ont pas besoin.
 */
function withXsrfToken(req: HttpRequest<unknown>): HttpRequest<unknown> {
  const method = req.method.toUpperCase();
  if (!['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
    return req;
  }
  const xsrfToken = readXsrfCookie();
  if (!xsrfToken) {
    return req;
  }
  return req.clone({ setHeaders: { 'X-XSRF-TOKEN': xsrfToken } });
}

/** Extrait la valeur du cookie `XSRF-TOKEN` depuis document.cookie. */
function readXsrfCookie(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * Gère un 401 : si l'utilisateur n'est pas connecté (aucune session), on
 * redirige simplement vers login. Sinon on renouvelle les cookies via
 * /refresh puis on rejoue la requête originale : les cookies fraîchement
 * renouvelés sont envoyés automatiquement, pas besoin de réinjecter un token.
 * En dernier recours (refresh impossible), on nettoie la session et on
 * redirige vers login.
 */
function handleUnauthorized(
  req: HttpRequest<unknown>,
  securedReq: HttpRequest<unknown>,
  next: HttpHandlerFn,
  originalError: HttpErrorResponse,
  authService: AuthService,
  router: Router,
  transloco: TranslocoService
): Observable<HttpEvent<unknown>> {
  // Pas de session du tout -> l'utilisateur n'est pas connecté
  if (!authService.isLoggedIn()) {
    router.navigate(['/auth/login']);
    return throwError(() => originalError);
  }

  return authService.refreshToken().pipe(
    switchMap(() => {
      // Rejoue la requête originale avec les cookies fraîchement renouvelés.
      // on re-lit le cookie XSRF (il peut avoir tourné lors du refresh) ; on
      const retryReq = withXsrfToken(securedReq.clone({ context: req.context.set(SKIP_AUTH, true) }));
      return sendWithTimeout(retryReq, next, transloco);
    }),
    catchError((refreshError) => {
      // Le refresh a échoué (cookie absent/expiré) -> session perdue
      authService.clearSession();
      router.navigate(['/auth/login']);
      return throwError(() => refreshError);
    })
  );
}
