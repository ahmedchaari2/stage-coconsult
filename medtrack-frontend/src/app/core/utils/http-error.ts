import { HttpErrorResponse } from '@angular/common/http';

/**
 * Extrait un message d'erreur exploitable du corps d'une réponse HTTP en échec. Le backend
 * renvoie soit `{ error: string }`, soit un objet par champ pour les erreurs de validation
 * (`{ champ: "msg", ... }`, pas de clé "error"), soit parfois un corps vide. On tolère aussi
 * une vieille clé "message" pour ne pas casser les endpoints qui l'utilisent encore.
 * Retourne null si rien d'exploitable (à l'appelant de retomber sur un message générique).
 * Le message renvoyé reste en français brut (texte du backend, non traduit) : afficher la
 * vraie raison de l'échec prime sur la cohérence i18n.
 */
export function extractBackendErrorMessage(err: unknown): string | null {
  if (!(err instanceof HttpErrorResponse)) {
    return null;
  }

  const body: unknown = err.error;
  if (body == null) {
    return null;
  }

  if (typeof body === 'string' && body.trim()) {
    return body;
  }

  if (typeof body === 'object') {
    const record = body as Record<string, unknown>;
    if (typeof record['error'] === 'string' && record['error'].trim()) {
      return record['error'];
    }
    if (typeof record['message'] === 'string' && record['message'].trim()) {
      return record['message'];
    }

    const fieldMessages = Object.values(record).filter((v): v is string => typeof v === 'string' && v.trim().length > 0);
    if (fieldMessages.length > 0) {
      return fieldMessages.join(' ');
    }
  }

  return null;
}
