import { Injectable, inject, signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';

export type SupportedLang = 'fr' | 'en';

const STORAGE_KEY = 'app-lang';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly translocoService = inject(TranslocoService);

  readonly currentLang = signal<SupportedLang>(this.readStoredLang());

  constructor() {
    this.translocoService.setActiveLang(this.currentLang());
  }

  setLanguage(lang: SupportedLang): void {
    this.currentLang.set(lang);
    this.translocoService.setActiveLang(lang);
    localStorage.setItem(STORAGE_KEY, lang);
  }

  private readStoredLang(): SupportedLang {
    return localStorage.getItem(STORAGE_KEY) === 'en' ? 'en' : 'fr';
  }
}
