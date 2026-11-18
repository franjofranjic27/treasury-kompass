import {
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CurrentClient } from '../core/current-client';
import { ClockPipe } from '../core/format';
import { ChatProposal, Citation } from '../core/models';
import { RefreshBus } from '../core/refresh-bus';
import { TreasuryApi } from '../core/treasury-api';
import { errorText } from '../pages/labels';
import { Badge } from '../shared/badge';
import { Icon } from '../shared/icon';

type ProposalState = 'open' | 'applied' | 'rejected' | 'failed';

interface ChatMessage {
  readonly role: 'user' | 'assistant';
  readonly time: Date;
  readonly text: string;
  readonly citations: readonly Citation[];
  readonly proposal: ChatProposal | null;
  readonly proposalState: ProposalState;
  readonly error: string | null;
}

/** Translation keys of the suggested questions (docs/specs/agent-testcases.md). */
const SUGGESTIONS: readonly string[] = [
  'chat.suggestions.fxFlows',
  'chat.suggestions.segment',
  'chat.suggestions.approval',
];

@Component({
  selector: 'app-chat-panel',
  imports: [FormsModule, RouterLink, TranslatePipe, ClockPipe, Badge, Icon],
  host: { class: 'chat' },
  template: `
    <header class="chat-head">
      <div class="chat-title">
        <app-icon name="chat" />
        <h2>{{ 'chat.title' | translate }}</h2>
        <span class="chat-context">
          @if (currentClient.name(); as name) {
            {{ 'chat.context' | translate }} <b>{{ name }}</b>
          } @else {
            {{ 'chat.noClientSelected' | translate }}
          }
        </span>
      </div>
      <p class="chat-scope">
        <app-icon name="lock" [size]="16" />
        <span>{{ 'chat.scope' | translate }}</span>
      </p>
    </header>

    <div class="chat-body" #body>
      @if (!messages().length) {
        <p class="meta chat-empty">
          {{ (currentClient.id() ? 'chat.emptyWithClient' : 'chat.emptyNoClient') | translate }}
        </p>
      }
      <ol
        class="chat-log"
        role="log"
        [attr.aria-label]="'chat.history' | translate"
        [attr.aria-busy]="busy()"
      >
        @for (m of messages(); track $index) {
          @if (m.role === 'user') {
            <li class="msg msg-user">
              <span class="msg-meta">{{ 'chat.advisor' | translate }} · {{ m.time | clock }}</span>
              <p class="bubble">{{ m.text }}</p>
            </li>
          } @else {
            <li class="msg msg-assistant">
              <span class="msg-meta">{{ 'chat.title' | translate }} · {{ m.time | clock }}</span>
              @if (m.text) {
                <p class="msg-text">{{ m.text }}</p>
              }
              @if (m.error) {
                <p class="error">{{ m.error }}</p>
              }
              @if (m.citations.length) {
                <ul class="citations" [attr.aria-label]="'chat.sources' | translate">
                  @for (c of m.citations; track $index) {
                    <li class="citation">
                      <app-icon name="document" [size]="16" />
                      <b>S{{ $index + 1 }}</b>
                      <span>{{ c.document }}, {{ c.location }}</span>
                    </li>
                  }
                </ul>
              }
              @if (m.proposal; as p) {
                <article class="proposal-card" [attr.aria-labelledby]="'proposal-' + $index">
                  <div class="proposal-head">
                    <h3 [id]="'proposal-' + $index">{{ p.title }}</h3>
                    <span class="tag-proposal">{{ 'common.proposal' | translate }}</span>
                  </div>
                  <p class="body-sm">{{ p.detail }}</p>
                  @switch (m.proposalState) {
                    @case ('applied') {
                      <div class="proposal-actions" role="status">
                        <app-badge tone="ok">{{ 'chat.applied' | translate }}</app-badge>
                        @if (currentClient.id(); as clientId) {
                          <a class="btn-link" [routerLink]="['/clients', clientId, 'beratung']">{{
                            'chat.viewInAdvisory' | translate
                          }}</a>
                        }
                      </div>
                    }
                    @case ('rejected') {
                      <div class="proposal-actions" role="status">
                        <app-badge tone="neutral">{{ 'chat.rejected' | translate }}</app-badge>
                        <button
                          class="btn-link"
                          type="button"
                          (click)="setProposalState($index, 'open')"
                        >
                          {{ 'chat.undo' | translate }}
                        </button>
                      </div>
                    }
                    @case ('failed') {
                      <div class="proposal-actions" role="status">
                        <app-badge tone="crit">{{ 'chat.applyFailed' | translate }}</app-badge>
                      </div>
                    }
                    @default {
                      <div class="proposal-actions">
                        <button
                          class="btn btn-primary"
                          type="button"
                          (click)="applyProposal($index, p)"
                        >
                          {{ 'chat.apply' | translate }}
                        </button>
                        <button
                          class="btn-link"
                          type="button"
                          (click)="setProposalState($index, 'rejected')"
                        >
                          {{ 'chat.reject' | translate }}
                        </button>
                      </div>
                    }
                  }
                  <p class="meta">{{ 'chat.proposalNote' | translate }}</p>
                </article>
              }
            </li>
          }
        }
      </ol>
      @if (busy()) {
        <p class="chat-busy" role="status">
          <app-icon name="progress" [size]="16" />
          <span>{{ 'chat.busy' | translate }}</span>
        </p>
      }

      <div class="suggestions">
        <span id="suggest-label" class="meta">{{ 'chat.suggestionsLabel' | translate }}</span>
        <ul aria-labelledby="suggest-label">
          @for (key of suggestions; track key) {
            <li>
              <button type="button" class="chip" [disabled]="busy()" (click)="sendSuggestion(key)">
                {{ key | translate }}
              </button>
            </li>
          }
        </ul>
      </div>
    </div>

    <form class="chat-form" (ngSubmit)="send(draft)">
      <div class="chat-input-row">
        <div class="field grow">
          <label for="chat-input">{{
            currentClient.name()
              ? ('chat.inputLabelClient' | translate: { name: currentClient.name() })
              : ('chat.inputLabel' | translate)
          }}</label>
          <textarea
            id="chat-input"
            name="draft"
            rows="2"
            [(ngModel)]="draft"
            (keydown.enter)="onEnter($event)"
          ></textarea>
        </div>
        <button
          class="send"
          type="submit"
          [attr.aria-label]="'chat.send' | translate"
          [disabled]="busy() || !draft.trim()"
        >
          <app-icon name="arrow-up" />
        </button>
      </div>
      <p class="meta">{{ 'chat.footnote' | translate }}</p>
    </form>
  `,
})
export class ChatPanel {
  protected readonly currentClient = inject(CurrentClient);
  private readonly api = inject(TreasuryApi);
  private readonly refresh = inject(RefreshBus);
  private readonly translate = inject(TranslateService);
  private readonly injector = inject(Injector);
  private readonly body = viewChild.required<ElementRef<HTMLElement>>('body');

  protected readonly suggestions = SUGGESTIONS;
  protected readonly messages = signal<readonly ChatMessage[]>([]);
  protected readonly busy = signal(false);
  protected draft = '';

  protected onEnter(event: Event): void {
    const keyboard = event as KeyboardEvent;
    if (keyboard.shiftKey) return;
    keyboard.preventDefault();
    void this.send(this.draft);
  }

  protected async send(text: string): Promise<void> {
    const message = text.trim();
    if (!message || this.busy()) return;
    this.draft = '';
    this.busy.set(true);
    this.messages.update((list) => [
      ...list,
      createMessage('user', message),
      createMessage('assistant', ''),
    ]);
    this.scrollToEnd();
    try {
      await this.api.chat(this.currentClient.id(), message, (event) => {
        switch (event.type) {
          case 'token':
            return this.patchLast((m) => ({ ...m, text: m.text + event.text }));
          case 'citation':
            return this.patchLast((m) => ({ ...m, citations: [...m.citations, event.citation] }));
          case 'proposal':
            return this.patchLast((m) => ({
              ...m,
              proposal: event.proposal,
              proposalState: 'open',
            }));
          case 'error':
            return this.patchLast((m) => ({ ...m, error: event.message }));
          case 'done':
            return;
        }
      });
    } catch (error) {
      this.patchLast((m) => ({
        ...m,
        error: this.translate.instant('chat.failed', { detail: errorText(error) }),
      }));
    } finally {
      this.busy.set(false);
      this.scrollToEnd();
    }
  }

  protected sendSuggestion(key: string): void {
    void this.send(this.translate.instant(key));
  }

  protected async applyProposal(index: number, proposal: ChatProposal): Promise<void> {
    const clientId = this.currentClient.id();
    if (!clientId) return;
    try {
      await this.api.setHedgeSettings(clientId, proposal.currency, {
        startRatio: proposal.startRatio,
        stepDown: proposal.stepDown,
      });
      this.setProposalState(index, 'applied');
      this.refresh.notify();
    } catch {
      this.setProposalState(index, 'failed');
    }
  }

  protected setProposalState(index: number, state: ProposalState): void {
    this.patchAt(index, (m) => ({ ...m, proposalState: state }));
  }

  private scrollToEnd(): void {
    afterNextRender(
      () => {
        const element = this.body().nativeElement;
        element.scrollTop = element.scrollHeight;
      },
      { injector: this.injector },
    );
  }

  private patchLast(change: (m: ChatMessage) => ChatMessage): void {
    this.patchAt(this.messages().length - 1, change);
  }

  private patchAt(index: number, change: (m: ChatMessage) => ChatMessage): void {
    this.messages.update((list) => list.map((m, i) => (i === index ? change(m) : m)));
  }
}

function createMessage(role: ChatMessage['role'], text: string): ChatMessage {
  return {
    role,
    time: new Date(),
    text,
    citations: [],
    proposal: null,
    proposalState: 'open',
    error: null,
  };
}
