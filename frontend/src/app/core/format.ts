import { Pipe, PipeTransform, inject } from '@angular/core';
import { Language, LanguageService } from './i18n/language';

interface LocaleConventions {
  readonly intl: string;
  readonly percentSuffix: string;
  readonly millionsSuffix: string;
}

/**
 * What Intl does not cover per UI language: unit spacing and the million abbreviation.
 * English uses en-GB so grouping is «1,250,000» (en-CH would group with apostrophes).
 */
const CONVENTIONS: Readonly<Record<Language, LocaleConventions>> = {
  'de-CH': { intl: 'de-CH', percentSuffix: ' %', millionsSuffix: ' Mio.' },
  en: { intl: 'en-GB', percentSuffix: '%', millionsSuffix: 'm' },
};

const numberFormats = new Map<string, Intl.NumberFormat>();
const timeFormats = new Map<Language, Intl.DateTimeFormat>();

function numberFormat(language: Language, decimals: number, signed: boolean): Intl.NumberFormat {
  const cacheKey = `${language}|${decimals}|${signed}`;
  let format = numberFormats.get(cacheKey);
  if (!format) {
    format = new Intl.NumberFormat(CONVENTIONS[language].intl, {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
      signDisplay: signed ? 'exceptZero' : 'auto',
    });
    numberFormats.set(cacheKey, format);
  }
  return format;
}

/** de-CH «1'250'000.5» with straight apostrophes, en «1,250,000.5»; negative numbers use a real minus sign. */
export function formatNumber(
  value: number,
  decimals: number,
  language: Language,
  signed = false,
): string {
  return numberFormat(language, decimals, signed)
    .format(value)
    .replace(/’/g, "'")
    .replace('-', '−');
}

/**
 * Base for the format pipes. They are impure because the output depends on the active language,
 * not only on their arguments; the formatters are cached, so re-running them is cheap.
 */
abstract class LocalizedPipe {
  private readonly language = inject(LanguageService);

  protected get lang(): Language {
    return this.language.current();
  }

  protected get conventions(): LocaleConventions {
    return CONVENTIONS[this.lang];
  }
}

/** Formats a plain number in the active locale, e.g. «1'250.5» or «1,250.5». */
@Pipe({ name: 'num', pure: false })
export class NumPipe extends LocalizedPipe implements PipeTransform {
  transform(value: number | null | undefined, decimals = 1): string {
    return value === null || value === undefined ? '–' : formatNumber(value, decimals, this.lang);
  }
}

/** Formats a value given in millions as «12.3 Mio.» (de-CH) or «12.3m» (en). */
@Pipe({ name: 'mio', pure: false })
export class MioPipe extends LocalizedPipe implements PipeTransform {
  transform(value: number | null | undefined, decimals = 1, signed = false): string {
    if (value === null || value === undefined) return '–';
    return `${formatNumber(value, decimals, this.lang, signed)}${this.conventions.millionsSuffix}`;
  }
}

/** Formats a value given in millions as full CHF amount, e.g. «CHF 1'395'000» or «CHF 1,395,000». */
@Pipe({ name: 'chf', pure: false })
export class ChfPipe extends LocalizedPipe implements PipeTransform {
  transform(valueInMillions: number | null | undefined, signed = false): string {
    if (valueInMillions === null || valueInMillions === undefined) return '–';
    return `CHF ${formatNumber(Math.round(valueInMillions * 1_000_000), 0, this.lang, signed)}`;
  }
}

/** Formats a ratio (0.58) or a percentage number (58) as «58 %» (de-CH) or «58%» (en). */
@Pipe({ name: 'pct', pure: false })
export class PctPipe extends LocalizedPipe implements PipeTransform {
  transform(value: number | null | undefined, isRatio = true, decimals = 0): string {
    if (value === null || value === undefined) return '–';
    const percent = isRatio ? value * 100 : value;
    return `${formatNumber(percent, decimals, this.lang)}${this.conventions.percentSuffix}`;
  }
}

/** Formats a time of day as «09:05» in the active locale. */
@Pipe({ name: 'clock', pure: false })
export class ClockPipe extends LocalizedPipe implements PipeTransform {
  transform(value: Date): string {
    let format = timeFormats.get(this.lang);
    if (!format) {
      format = new Intl.DateTimeFormat(this.conventions.intl, {
        hour: '2-digit',
        minute: '2-digit',
      });
      timeFormats.set(this.lang, format);
    }
    return format.format(value);
  }
}
