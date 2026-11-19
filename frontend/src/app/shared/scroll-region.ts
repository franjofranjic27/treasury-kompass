import {
  DestroyRef,
  Directive,
  ElementRef,
  afterNextRender,
  effect,
  inject,
  input,
} from '@angular/core';

/**
 * Makes a horizontally scrolling container reachable by keyboard (WCAG 2.1.1), but only while it
 * actually scrolls, so wide screens get no extra tab stops. Returns a function that stops watching.
 */
export function watchScrollRegion(element: HTMLElement, label: () => string): () => void {
  const sync = () => {
    if (element.scrollWidth > element.clientWidth + 1) {
      element.tabIndex = 0;
      element.setAttribute('role', 'region');
      element.setAttribute('aria-label', label());
    } else {
      element.removeAttribute('tabindex');
      element.removeAttribute('role');
      element.removeAttribute('aria-label');
    }
  };
  const observer = new ResizeObserver(sync);
  observer.observe(element);
  // The content can change width while the container keeps its size (data or language switch).
  if (element.firstElementChild) observer.observe(element.firstElementChild);
  sync();
  return () => observer.disconnect();
}

/** Template counterpart of {@link watchScrollRegion}: `<div class="table-wrap" [appScrollRegion]="label">`. */
@Directive({ selector: '[appScrollRegion]' })
export class ScrollRegion {
  /** Accessible name of the region while it scrolls, e.g. the table's title. */
  readonly appScrollRegion = input.required<string>();

  constructor() {
    const element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    let stop: (() => void) | null = null;
    afterNextRender(() => (stop = watchScrollRegion(element, () => this.appScrollRegion())));
    // A label change (language switch) must reach an already scrolling region.
    effect(() => {
      const label = this.appScrollRegion();
      if (element.hasAttribute('aria-label')) element.setAttribute('aria-label', label);
    });
    inject(DestroyRef).onDestroy(() => stop?.());
  }
}
