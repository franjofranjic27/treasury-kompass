import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  ViewEncapsulation,
  afterRenderEffect,
  computed,
  inject,
  input,
} from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { watchScrollRegion } from '../shared/scroll-region';
import { ChatSource, renderMarkdown } from './markdown';

/**
 * Rendered markdown of one assistant answer. Re-renders only when its text or sources change; the chat
 * panel throttles those changes while an answer streams in.
 */
@Component({
  selector: 'app-chat-markdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // The rendered HTML carries no Angular attributes, so its styles cannot be view-encapsulated.
  encapsulation: ViewEncapsulation.None,
  styleUrl: './chat-markdown.scss',
  template: `<div class="md" [innerHTML]="html()"></div>`,
})
export class ChatMarkdown {
  readonly text = input.required<string>();
  readonly sources = input.required<readonly ChatSource[]>();

  protected readonly html = computed(() => renderMarkdown(this.text(), this.sources()));

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    const translate = inject(TranslateService);
    let stopWatching: readonly (() => void)[] = [];
    const unwatch = () => stopWatching.forEach((stop) => stop());
    // Wide tables scroll inside the bubble; each render replaces them, so they are watched anew.
    afterRenderEffect(() => {
      this.html();
      unwatch();
      stopWatching = Array.from(host.querySelectorAll<HTMLElement>('.md-table'), (table) =>
        watchScrollRegion(table, () => translate.instant('chat.table')),
      );
    });
    inject(DestroyRef).onDestroy(unwatch);
  }
}
