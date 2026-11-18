import { Language, SUPPORTED_LANGUAGES } from './language';

/** Text of the in-memory demo backend in every supported language. UI strings live in public/i18n instead. */
export type Localized = Readonly<Record<Language, string>>;

export const l = (de: string, en: string): Localized => ({ 'de-CH': de, en });

/** FIDLEG references stay as they are in German; English names the act as «FinSA (FIDLEG)». */
export const law = (de: string, en: string = de): Localized =>
  l(`${de} FIDLEG`, `${en} FinSA (FIDLEG)`);

/** Lifts a plain string to the same text in every language. */
export const toLocalized = (value: string | Localized): Localized =>
  typeof value === 'string' ? l(value, value) : value;

/** Mirrors a model type, but lets every string be given as {@link Localized} demo text. */
export type Localizable<T> = T extends string
  ? T | Localized
  : T extends readonly (infer U)[]
    ? readonly Localizable<U>[]
    : T extends object
      ? { readonly [K in keyof T]: Localizable<T[K]> }
      : T;

/** Picks the language variant of every {@link Localized} leaf, returning the plain model shape. */
export function resolveLocalized<T>(value: Localizable<T>, language: Language): T {
  // The walk only replaces Localized leaves, so the result has exactly the shape Localizable<T> was derived from.
  return resolveValue(value, language) as T;
}

function resolveValue(value: unknown, language: Language): unknown {
  if (isLocalized(value)) return value[language];
  if (Array.isArray(value)) return value.map((item) => resolveValue(item, language));
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [key, resolveValue(item, language)]),
    );
  }
  return value;
}

function isLocalized(value: unknown): value is Localized {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) return false;
  const keys = Object.keys(value);
  return (
    keys.length === SUPPORTED_LANGUAGES.length &&
    SUPPORTED_LANGUAGES.every(
      (lang) => typeof (value as Record<string, unknown>)[lang] === 'string',
    )
  );
}
