/** localStorage key of the «Demo-Daten» switch, see `frontend/src/app/core/data-mode.ts`. */
export const DATA_MODE_STORAGE_KEY = 'treasury-kompass.useMock';

/**
 * Browser storage as if the advisor had chosen demo data in an earlier session. The app then skips
 * the startup probe for a backend and never shows the fallback notice.
 */
export function demoModeStorageState(origin: string) {
  return {
    cookies: [],
    origins: [{ origin, localStorage: [{ name: DATA_MODE_STORAGE_KEY, value: 'true' }] }],
  };
}

/** Browser storage without any choice: the app starts with its default, the real backend. */
export const NO_STORED_CHOICE = { cookies: [], origins: [] };
