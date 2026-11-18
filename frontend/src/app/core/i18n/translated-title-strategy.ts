import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';

const PRODUCT = 'Treasury Kompass';

/** Route titles are translation keys; the document title follows language switches. */
@Injectable()
export class TranslatedTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly translate = inject(TranslateService);
  private lastSnapshot: RouterStateSnapshot | null = null;

  constructor() {
    super();
    this.translate.onLangChange.subscribe(() => {
      if (this.lastSnapshot) this.updateTitle(this.lastSnapshot);
    });
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.lastSnapshot = snapshot;
    const key = this.buildTitle(snapshot);
    this.title.setTitle(key ? `${this.translate.instant(key)} · ${PRODUCT}` : PRODUCT);
  }
}
