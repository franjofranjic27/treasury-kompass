import { DOCUMENT, Injectable, inject, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Observable, tap } from 'rxjs';
import { RefreshBus } from '../refresh-bus';

export const SUPPORTED_LANGUAGES = ['de-CH', 'en'] as const;
export type Language = (typeof SUPPORTED_LANGUAGES)[number];
export const DEFAULT_LANGUAGE: Language = 'de-CH';

export interface LanguageOption {
  readonly code: Language;
  readonly short: string;
  /** Endonym, shown in its own language regardless of the active UI language. */
  readonly name: string;
}

export const LANGUAGE_OPTIONS: readonly LanguageOption[] = [
  { code: 'de-CH', short: 'DE', name: 'Deutsch' },
  { code: 'en', short: 'EN', name: 'English' },
];

const STORAGE_KEY = 'treasury-kompass.language';

export function isLanguage(value: unknown): value is Language {
  return typeof value === 'string' && (SUPPORTED_LANGUAGES as readonly string[]).includes(value);
}

function readStoredLanguage(): Language {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return isLanguage(stored) ? stored : DEFAULT_LANGUAGE;
  } catch {
    return DEFAULT_LANGUAGE;
  }
}

function writeStoredLanguage(language: Language): void {
  try {
    localStorage.setItem(STORAGE_KEY, language);
  } catch {
    // Storage blocked (private mode): the choice then only lasts until reload, which is acceptable.
  }
}

/**
 * Owns the active UI language: loads the translation file, keeps `<html lang>` in sync and persists the choice.
 * Format pipes read {@link current}; screens reload on a switch because the mock backend localizes its content.
 */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly translate = inject(TranslateService);
  private readonly document = inject(DOCUMENT);
  private readonly refresh = inject(RefreshBus);
  private readonly active = signal<Language>(readStoredLanguage());

  readonly current = this.active.asReadonly();

  /** Loads the stored language before the first render, so no raw translation keys flash up. */
  init(): Observable<unknown> {
    return this.apply(this.active());
  }

  switchTo(language: Language): void {
    if (language === this.active()) return;
    this.apply(language).subscribe(() => {
      this.active.set(language);
      writeStoredLanguage(language);
      this.refresh.notify();
    });
  }

  private apply(language: Language): Observable<unknown> {
    return this.translate
      .use(language)
      .pipe(tap(() => (this.document.documentElement.lang = language)));
  }
}
