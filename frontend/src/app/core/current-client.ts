import { Injectable, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter, map } from 'rxjs';

const CLIENT_URL = /^\/clients\/([^/?#]+)/;

/** Derives the selected client from the URL so shell, nav and chat stay in sync without extra state. */
@Injectable({ providedIn: 'root' })
export class CurrentClient {
  private readonly router = inject(Router);

  readonly id = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => CLIENT_URL.exec(e.urlAfterRedirects)?.[1] ?? null)
    ),
    { initialValue: null }
  );
}
