import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Translation, TranslocoLoader } from '@jsverse/transloco';

import { environment } from 'src/environments/environment';

/**
 * Charge les fichiers de traduction depuis src/assets/i18n/<lang>.json.
 * En dev, un paramètre de cache-busting force le rechargement à chaque appel
 * pour que les éditions de fr.json/en.json soient visibles sans purge manuelle
 * du cache navigateur ; en prod, aucun paramètre n'est ajouté (cache HTTP normal).
 */
@Injectable({ providedIn: 'root' })
export class TranslocoHttpLoader implements TranslocoLoader {
  private readonly http = inject(HttpClient);

  getTranslation(lang: string) {
    const cacheBust = environment.production ? '' : `?t=${Date.now()}`;
    return this.http.get<Translation>(`assets/i18n/${lang}.json${cacheBust}`);
  }
}
