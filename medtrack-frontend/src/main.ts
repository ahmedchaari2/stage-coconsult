import { enableProdMode, importProvidersFrom, provideAppInitializer, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { provideTransloco } from '@jsverse/transloco';

import { environment } from './environments/environment';
import { BrowserModule, bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import localeEn from '@angular/common/locales/en';
import { AppRoutingModule } from './app/app-routing.module';
import { AppComponent } from './app/app.component';
import { authInterceptor } from './app/core/interceptors/auth.interceptor';
import { AuthService } from './app/core/services/auth.service';
import { LanguageService } from './app/core/services/language.service';
import { TranslocoHttpLoader } from './app/core/transloco/transloco-http-loader';

if (environment.production) {
  enableProdMode();
}

// Requis par formatDate()/DatePipe pour ces locales (utilisées par AppDatepickerI18n pour le calendrier du datepicker).
registerLocaleData(localeFr, 'fr');
registerLocaleData(localeEn, 'en');

bootstrapApplication(AppComponent, {
  providers: [
    importProvidersFrom(BrowserModule, AppRoutingModule),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideTransloco({
      config: {
        availableLangs: ['fr', 'en'],
        defaultLang: 'fr',
        fallbackLang: 'fr',
        reRenderOnLangChange: true,
        prodMode: environment.production
      },
      loader: TranslocoHttpLoader
    }),
    // Force l'instanciation de LanguageService avant le premier rendu, sinon son constructeur
    // (qui applique la langue persistée en localStorage) arrive trop tard et on voit un flash fr.
    provideAppInitializer(() => {
      inject(LanguageService);
    }),
    // Restaure la session via GET /api/auth/me avant d'afficher l'app (cookies envoyés
    // automatiquement). Angular 22 n'a plus de feature withCredentials() globale, donc c'est
    // l'intercepteur qui pose withCredentials: true sur chaque requête.
    provideAppInitializer(() => {
      const auth = inject(AuthService);
      // firstValueFrom garde la souscription active jusqu'à la fin réelle de la requête /me,
      // sinon elle est annulée par le cycle de vie du bootstrap (AbortError fetch). Un échec
      // (401, pas de session) fait juste démarrer l'app non connectée, sans erreur bloquante.
      return firstValueFrom(auth.bootstrapSession());
    })
  ]
}).catch((err) => console.error(err));