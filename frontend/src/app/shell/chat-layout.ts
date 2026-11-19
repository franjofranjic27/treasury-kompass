import { DOCUMENT, DestroyRef, Injectable, computed, inject, signal } from '@angular/core';

const STORAGE_KEY = 'treasury-kompass.chatOpen';

/** Below 1024 px the chat is an overlay drawer; keep in sync with `$narrow` in app.scss. */
const NARROW_VIEWPORT = '(max-width: 1023.98px)';

function readStoredOpen(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) !== 'false';
  } catch {
    return true;
  }
}

function writeStoredOpen(open: boolean): void {
  try {
    localStorage.setItem(STORAGE_KEY, String(open));
  } catch {
    // Storage blocked (private mode): the choice then only lasts until reload, which is acceptable.
  }
}

/**
 * Whether the copilot chat is shown. On wide viewports it is a side column whose state is remembered;
 * on narrow ones it is a drawer that always starts closed, so it never covers the page unasked.
 */
@Injectable({ providedIn: 'root' })
export class ChatLayout {
  private readonly query = inject(DOCUMENT).defaultView?.matchMedia(NARROW_VIEWPORT) ?? null;
  private readonly isNarrow = signal(this.query?.matches ?? false);
  private readonly columnOpen = signal(readStoredOpen());
  private readonly drawerOpen = signal(false);

  readonly narrow = this.isNarrow.asReadonly();
  readonly open = computed(() => (this.isNarrow() ? this.drawerOpen() : this.columnOpen()));

  constructor() {
    const query = this.query;
    if (!query) return;
    const onChange = (event: MediaQueryListEvent) => {
      this.drawerOpen.set(false);
      this.isNarrow.set(event.matches);
    };
    query.addEventListener('change', onChange);
    inject(DestroyRef).onDestroy(() => query.removeEventListener('change', onChange));
  }

  toggle(): void {
    this.setOpen(!this.open());
  }

  close(): void {
    this.setOpen(false);
  }

  private setOpen(open: boolean): void {
    if (this.isNarrow()) {
      this.drawerOpen.set(open);
      return;
    }
    this.columnOpen.set(open);
    writeStoredOpen(open);
  }
}
