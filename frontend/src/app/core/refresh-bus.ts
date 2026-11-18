import { Injectable, signal } from '@angular/core';

/** Lets the chat panel tell the open screen to reload after a proposal was applied. */
@Injectable({ providedIn: 'root' })
export class RefreshBus {
  private readonly counter = signal(0);
  readonly tick = this.counter.asReadonly();

  notify(): void {
    this.counter.update((n) => n + 1);
  }
}
