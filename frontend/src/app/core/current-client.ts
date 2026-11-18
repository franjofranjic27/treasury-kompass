import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter, map } from 'rxjs';
import { TreasuryApi } from './treasury-api';

export type ClientSection = 'dashboard' | 'profilierung' | 'beratung';

const CLIENT_URL = /^\/clients\/([^/?#]+)(?:\/(profilierung|beratung))?/;

interface ClientName {
  readonly id: string;
  readonly name: string;
}

/**
 * Derives the selected client and the open section from the URL, so shell, breadcrumb and chat
 * stay in sync without extra state. The name is loaded once per client for the breadcrumb.
 */
@Injectable({ providedIn: 'root' })
export class CurrentClient {
  private readonly router = inject(Router);
  private readonly api = inject(TreasuryApi);
  private readonly loadedName = signal<ClientName | null>(null);

  private readonly match = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => CLIENT_URL.exec(e.urlAfterRedirects)),
    ),
    { initialValue: null },
  );

  readonly id = computed(() => this.match()?.[1] ?? null);

  readonly section = computed<ClientSection | null>(() => {
    const match = this.match();
    if (!match) return null;
    return match[2] === 'profilierung' || match[2] === 'beratung' ? match[2] : 'dashboard';
  });

  readonly name = computed(() => {
    const loaded = this.loadedName();
    return loaded && loaded.id === this.id() ? loaded.name : null;
  });

  constructor() {
    effect(() => {
      const id = this.id();
      if (id && untracked(this.loadedName)?.id !== id) void this.loadName(id);
    });
  }

  private async loadName(id: string): Promise<void> {
    try {
      const client = await this.api.getClient(id);
      this.loadedName.set({ id, name: client.name });
    } catch {
      // The page itself reports load errors; the breadcrumb then shows a neutral placeholder.
      this.loadedName.set(null);
    }
  }
}
