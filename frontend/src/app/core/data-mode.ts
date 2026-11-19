import { DOCUMENT, Injectable, computed, inject, signal } from '@angular/core';
import { environment } from '../../environments/environment';
import { isJsonResponse } from './content-type';

const STORAGE_KEY = 'treasury-kompass.useMock';
const PROBE_URL = '/api/me';
const PROBE_TIMEOUT_MS = 5000;

/**
 * Why the shell shows a backend notice: no backend answered at startup, so the app fell back to
 * demo data; the probe before leaving demo mode failed; or a live request found no backend.
 */
export type BackendNoticeKind = 'startupFallback' | 'probeFailed' | 'unavailable';

/** The advisor's explicit choice from an earlier session, or null if they never used the switch. */
function readStoredChoice(): boolean | null {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === null ? null : stored === 'true';
  } catch {
    return null;
  }
}

function writeStoredChoice(useMock: boolean): void {
  try {
    localStorage.setItem(STORAGE_KEY, String(useMock));
  } catch {
    // Storage blocked (private mode): the choice then only lasts until reload, which is acceptable.
  }
}

/**
 * Owns the switch between demo data and the real backend. The mode is settled once by {@link init},
 * before the first screen; switching reloads the app so the API provider is rebuilt. Leaving demo
 * mode requires `/api/me` to answer with JSON, so the switch never leads to a missing backend.
 */
@Injectable({ providedIn: 'root' })
export class DataMode {
  private readonly location = inject(DOCUMENT).location;
  private readonly storedChoice = readStoredChoice();
  private readonly mock = signal(this.storedChoice ?? environment.useMock);
  private readonly checking = signal(false);
  private readonly currentNotice = signal<BackendNoticeKind | null>(null);

  readonly useMock = this.mock.asReadonly();
  readonly probing = this.checking.asReadonly();
  readonly notice = this.currentNotice.asReadonly();
  /** True while live requests find no backend; screens then leave the explanation to the shell. */
  readonly backendUnavailable = computed(() => this.currentNotice() === 'unavailable');

  /**
   * Runs once before the first screen. Without a stored choice the app starts on the backend if one
   * answers, otherwise on demo data with a notice. The fallback is not stored, so the next start
   * tries the backend again. A stored choice is kept as is: whoever chose the backend sees the
   * outage banner instead of silently getting fictitious data.
   */
  async init(): Promise<void> {
    if (this.mock() || this.storedChoice !== null) return;
    if (await probeBackend()) return;
    this.mock.set(true);
    this.currentNotice.set('startupFallback');
  }

  /** Switches to the backend if it answers; otherwise stays in demo mode and returns false. */
  async useBackend(): Promise<boolean> {
    this.checking.set(true);
    const reachable = await probeBackend();
    this.checking.set(false);
    if (!reachable) {
      this.currentNotice.set('probeFailed');
      return false;
    }
    this.reloadWith(false);
    return true;
  }

  useDemoData(): void {
    this.reloadWith(true);
  }

  /** Called by the HTTP client when a request found no backend; the shell offers demo data. */
  reportUnavailable(): void {
    this.currentNotice.set('unavailable');
  }

  /** Called by the HTTP client when the backend answered again. */
  reportAvailable(): void {
    if (this.currentNotice() === 'unavailable') this.currentNotice.set(null);
  }

  dismissNotice(): void {
    this.currentNotice.set(null);
  }

  private reloadWith(useMock: boolean): void {
    writeStoredChoice(useMock);
    this.location.assign('/');
  }
}

async function probeBackend(): Promise<boolean> {
  try {
    const response = await fetch(PROBE_URL, {
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(PROBE_TIMEOUT_MS),
    });
    if (!response.ok || !isJsonResponse(response)) return false;
    await response.json();
    return true;
  } catch {
    // Network error, timeout or a body that is not JSON: there is no backend behind /api.
    return false;
  }
}
