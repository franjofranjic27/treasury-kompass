import { Injectable, signal } from '@angular/core';

/**
 * Tells the open screen to reload: after the chat panel applied a proposal, or after a language switch,
 * because the backend (and the mock) delivers rule texts in the requested language.
 */
@Injectable({ providedIn: 'root' })
export class RefreshBus {
  private readonly counter = signal(0);
  readonly tick = this.counter.asReadonly();

  notify(): void {
    this.counter.update((n) => n + 1);
  }
}
