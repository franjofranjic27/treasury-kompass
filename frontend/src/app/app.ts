import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { CurrentClient } from './core/current-client';
import { readUseMock, writeUseMock } from './core/data-mode';
import { LANGUAGE_OPTIONS, LanguageService } from './core/i18n/language';
import { Icon } from './shared/icon';
import { jumpTo } from './shared/jump-to';
import { ChatPanel } from './shell/chat-panel';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, TranslatePipe, ChatPanel, Icon],
  template: `
    <a class="skip-link" href="#main" (click)="jumpTo($event, 'main')">{{
      'app.skipToContent' | translate
    }}</a>
    <header class="topbar">
      <a class="brand" routerLink="/" [attr.aria-label]="'app.brandHome' | translate"
        >Treasury Kompass</a
      >
      <nav class="breadcrumb" [attr.aria-label]="'app.breadcrumb' | translate">
        <ol>
          @if (currentClient.id(); as id) {
            <li>
              <a routerLink="/">{{ 'nav.clients' | translate }}</a>
              <app-icon name="chevron-right" />
            </li>
            @if (currentClient.section() === 'dashboard') {
              <li>
                <span aria-current="page">{{ currentClient.name() ?? '…' }}</span>
              </li>
            } @else {
              <li>
                <a [routerLink]="['/clients', id]">{{ currentClient.name() ?? '…' }}</a>
                <app-icon name="chevron-right" />
              </li>
              <li>
                <span aria-current="page">{{ 'nav.' + currentClient.section() | translate }}</span>
              </li>
            }
          } @else {
            <li>
              <span aria-current="page">{{ 'nav.clients' | translate }}</span>
            </li>
          }
        </ol>
      </nav>
      <div class="topbar-tools">
        <div class="lang-switch" role="group" [attr.aria-label]="'app.language' | translate">
          @for (option of languages; track option.code) {
            <button
              type="button"
              [attr.aria-pressed]="language.current() === option.code"
              (click)="language.switchTo(option.code)"
            >
              <span aria-hidden="true">{{ option.short }}</span>
              <span class="sr-only" [attr.lang]="option.code"
                >{{ option.short }} {{ option.name }}</span
              >
            </button>
          }
        </div>
        <label class="toggle">
          <input type="checkbox" [checked]="useMock" (change)="toggleMock()" />
          {{ 'app.demoData' | translate }}
        </label>
        <div class="advisor">
          <span class="avatar" aria-hidden="true"><app-icon name="user" /></span>
          <span class="advisor-text">
            <b>{{ 'app.advisorName' | translate }}</b>
            <span>{{ 'app.advisorRole' | translate }}</span>
          </span>
        </div>
      </div>
    </header>
    <div class="layout">
      <main id="main" class="content" tabindex="-1">
        <router-outlet />
        <footer class="footer">{{ 'app.footer' | translate }}</footer>
      </main>
      <aside class="chat-aside" [attr.aria-label]="'chat.title' | translate">
        <app-chat-panel />
      </aside>
    </div>
  `,
})
export class App {
  protected readonly currentClient = inject(CurrentClient);
  protected readonly language = inject(LanguageService);
  protected readonly languages = LANGUAGE_OPTIONS;
  protected readonly useMock = readUseMock();
  protected readonly jumpTo = jumpTo;

  protected toggleMock(): void {
    writeUseMock(!this.useMock);
    location.assign('/');
  }
}
