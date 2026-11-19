import { Component, ElementRef, Injector, afterNextRender, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { filter } from 'rxjs';
import { CurrentClient } from './core/current-client';
import { DataMode } from './core/data-mode';
import { LANGUAGE_OPTIONS, LanguageService } from './core/i18n/language';
import { Icon } from './shared/icon';
import { jumpTo } from './shared/jump-to';
import { BackendNotice } from './shell/backend-notice';
import { ChatLayout } from './shell/chat-layout';
import { ChatPanel } from './shell/chat-panel';

/**
 * App shell fixed to the viewport: header, body and footer rows. Only the main area and the chat
 * history scroll, so the document itself never does.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, TranslatePipe, BackendNotice, ChatPanel, Icon],
  styleUrl: './app.scss',
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
            @let clientName = currentClient.name() ?? '…';
            <li>
              <a routerLink="/" [title]="'nav.clients' | translate">{{
                'nav.clients' | translate
              }}</a>
              <app-icon name="chevron-right" />
            </li>
            @if (currentClient.section() === 'dashboard') {
              <li class="crumb-client">
                <span aria-current="page" [title]="clientName">{{ clientName }}</span>
              </li>
            } @else {
              @let section = 'nav.' + currentClient.section() | translate;
              <li class="crumb-client">
                <a [routerLink]="['/clients', id]" [title]="clientName">{{ clientName }}</a>
                <app-icon name="chevron-right" />
              </li>
              <li>
                <span aria-current="page" [title]="section">{{ section }}</span>
              </li>
            }
          } @else {
            <li>
              <span aria-current="page" [title]="'nav.clients' | translate">{{
                'nav.clients' | translate
              }}</span>
            </li>
          }
        </ol>
      </nav>
      <div class="topbar-tools">
        <div class="lang-switch" role="group" [attr.aria-label]="'app.language' | translate">
          @for (option of languages; track option.code) {
            <button
              type="button"
              [attr.data-testid]="'lang-' + option.short.toLowerCase()"
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
          <input
            type="checkbox"
            data-testid="mock-toggle"
            [checked]="dataMode.useMock"
            [disabled]="dataMode.probing()"
            (change)="toggleMock($event)"
          />
          {{ 'app.demoData' | translate }}
        </label>
        <div class="advisor">
          <span class="avatar" aria-hidden="true"><app-icon name="user" /></span>
          <span class="advisor-text">
            <b>{{ 'app.advisorName' | translate }}</b>
            <span>{{ 'app.advisorRole' | translate }}</span>
          </span>
        </div>
        <button
          #chatToggle
          type="button"
          class="chat-toggle"
          data-testid="chat-toggle"
          aria-controls="chat-panel"
          [attr.aria-expanded]="chat.open()"
          (click)="toggleChat()"
        >
          <app-icon name="chat" />
          <span>{{ 'chat.title' | translate }}</span>
        </button>
      </div>
    </header>
    <div class="layout">
      <main #main id="main" class="content" tabindex="-1" data-testid="app-main">
        @if (dataMode.notice(); as notice) {
          <app-backend-notice [notice]="notice" />
        }
        <router-outlet />
      </main>
      <aside
        id="chat-panel"
        class="chat-aside"
        data-testid="chat-panel"
        [attr.aria-label]="'chat.title' | translate"
        [hidden]="!chat.open()"
        (keydown.escape)="closeDrawer()"
      >
        <app-chat-panel />
      </aside>
    </div>
    <footer class="footer">{{ 'app.footer' | translate }}</footer>
  `,
})
export class App {
  protected readonly currentClient = inject(CurrentClient);
  protected readonly language = inject(LanguageService);
  protected readonly dataMode = inject(DataMode);
  protected readonly chat = inject(ChatLayout);
  protected readonly languages = LANGUAGE_OPTIONS;
  protected readonly jumpTo = jumpTo;

  private readonly injector = inject(Injector);
  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');
  private readonly chatToggle = viewChild.required<ElementRef<HTMLButtonElement>>('chatToggle');
  private readonly chatPanel = viewChild.required(ChatPanel);

  constructor() {
    // Only the main area scrolls, and the router's scroll restoration knows only the window.
    inject(Router)
      .events.pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.main().nativeElement.scrollTo({ top: 0 }));
  }

  protected async toggleMock(event: Event): Promise<void> {
    const checkbox = event.target as HTMLInputElement;
    if (checkbox.checked) {
      this.dataMode.useDemoData();
      return;
    }
    const switched = await this.dataMode.useBackend();
    // The binding still holds `true`, so Angular would not restore the box by itself.
    if (!switched) checkbox.checked = true;
  }

  protected toggleChat(): void {
    this.chat.toggle();
    // As an overlay the drawer takes focus, so keyboard users land in it right away.
    if (this.chat.narrow() && this.chat.open()) {
      afterNextRender(() => this.chatPanel().focusInput(), { injector: this.injector });
    }
  }

  protected closeDrawer(): void {
    if (!this.chat.narrow()) return;
    this.chat.close();
    this.chatToggle().nativeElement.focus();
  }
}
