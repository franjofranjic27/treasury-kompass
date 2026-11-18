import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withFetch } from '@angular/common/http';
import {
  TitleStrategy,
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
} from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { provideTreasuryApi } from './core/api.providers';
import { DEFAULT_LANGUAGE, LanguageService } from './core/i18n/language';
import { TranslatedTitleStrategy } from './core/i18n/translated-title-strategy';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled' }),
    ),
    { provide: TitleStrategy, useClass: TranslatedTitleStrategy },
    provideHttpClient(withFetch()),
    // One build for all languages: translations are fetched at runtime from public/i18n.
    provideTranslateService({
      loader: provideTranslateHttpLoader({ prefix: '/i18n/', suffix: '.json' }),
      fallbackLang: DEFAULT_LANGUAGE,
    }),
    provideAppInitializer(() => inject(LanguageService).init()),
    provideTreasuryApi(),
  ],
};
