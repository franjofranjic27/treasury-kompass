import { DOCUMENT, Injectable, computed, inject, signal } from '@angular/core';
import { environment } from '../../environments/environment';
import { isJsonResponse } from './content-type';

const STORAGE_KEY = 'treasury-kompass.useMock';
const PROBE_URL = '/api/me';
const PROBE_TIMEOUT_MS = 5000;

/** Why the shell shows a backend notice: the probe before leaving demo mode failed, or a live request did. */
export type BackendNoticeKind = 'probeFailed' | 'unavailable';

/** Reads the data mode once at bootstrap; switching reloads the app so the API provider is rebuilt. */
export function readUseMock(): boolean {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === null ? environment.useMock : stored === 'true';
  } catch {
    return environment.useMock;
  }
}

function writeUseMock(useMock: boolean): void {
  try {
    localStorage.setItem(STORAGE_KEY, String(useMock));
  } catch {
    // Storage blocked (private mode): the choice then only lasts until reload, which is acceptable.
  }
}

/**
 * Owns the switch between demo data and the real backend. Demo mode is only left when `/api/me`
 * answers with JSON, so a static deployment without backend (Vercel) cannot end up in a broken state.
 */
@Injectable({ providedIn: 'root' })
export class DataMode {
  private readonly location = inject(DOCUMENT).location;
  private readonly checking = signal(false);
  private readonly currentNotice = signal<BackendNoticeKind | null>(null);

  readonly useMock = readUseMock();
  readonly probing = this.checking.asReadonly();
  readonly notice = this.currentNotice.asReadonly();
  /** True while live requests find no backend; screens then leave the explanation to the shell. */
  readonly backendUnavailable = computed(() => this.currentNotice() === 'unavailable');

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
    writeUseMock(useMock);
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
