import { environment } from '../../environments/environment';

const STORAGE_KEY = 'treasury-kompass.useMock';

/** Reads the data mode once at bootstrap; switching reloads the app so the API provider is rebuilt. */
export function readUseMock(): boolean {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === null ? environment.useMock : stored === 'true';
  } catch {
    return environment.useMock;
  }
}

export function writeUseMock(useMock: boolean): void {
  try {
    localStorage.setItem(STORAGE_KEY, String(useMock));
  } catch {
    // Storage blocked (private mode): the toggle then only lasts until reload, which is acceptable.
  }
}
