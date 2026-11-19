import {
  Component,
  DestroyRef,
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
import { ChatEvent, ChatProposal, Citation, HedgeDraft } from '../core/models';
import { RefreshBus } from '../core/refresh-bus';
import { TreasuryApi } from '../core/treasury-api';
import { errorText } from '../pages/labels';
import { Badge } from '../shared/badge';
import { Icon } from '../shared/icon';
import { ChatMarkdown } from './chat-markdown';
import { ChatSource } from './markdown';

type ProposalState = 'open' | 'applied' | 'rejected' | 'failed';

interface ChatMessage {
  readonly role: 'user' | 'assistant';
  readonly time: Date;
  readonly text: string;
  readonly sources: readonly ChatSource[];
  readonly proposal: ChatProposal | null;
  readonly proposalState: ProposalState;
  readonly error: string | null;
}

/** Translation keys of the suggested questions (docs/specs/agent-testcases.md). */
const SUGGESTIONS: readonly string[] = [
  'chat.suggestions.balance',
  'chat.suggestions.fxFlows',
  'chat.suggestions.segment',
  'chat.suggestions.approval',
];

/** Streamed tokens are applied in batches, so the markdown re-renders at most this often. */
const RENDER_INTERVAL_MS = 100;
/** While an answer streams in, the log keeps scrolling only if the advisor has not scrolled away. */
const FOLLOW_THRESHOLD_PX = 80;

@Component({
  selector: 'app-chat-panel',
  imports: [FormsModule, RouterLink, TranslatePipe, ClockPipe, Badge, Icon, ChatMarkdown],
  styleUrl: './chat-panel.scss',
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
      <!-- The live region wraps the list, so the list keeps its list semantics. -->
      <div
        class="chat-log"
        role="log"
        [attr.aria-label]="'chat.history' | translate"
        [attr.aria-busy]="busy()"
      >
        @if (!messages().length) {
          <p class="meta">
            {{ (currentClient.id() ? 'chat.emptyWithClient' : 'chat.emptyNoClient') | translate }}
          </p>
        }
        <ol class="messages">
          @for (m of messages(); track $index) {
            @if (m.role === 'user') {
              <li class="msg msg-user" data-testid="chat-message" data-role="user">
                <span class="msg-meta"
                  >{{ 'chat.advisor' | translate }} · {{ m.time | clock }}</span
                >
                <p class="bubble" data-testid="chat-message-body">{{ m.text }}</p>
              </li>
            } @else {
              <li class="msg" data-testid="chat-message" data-role="assistant">
                <span class="msg-meta">{{ 'chat.title' | translate }} · {{ m.time | clock }}</span>
                @if (m.text) {
                  <app-chat-markdown
                    data-testid="chat-message-body"
                    [text]="m.text"
                    [sources]="m.sources"
                  />
                }
                @if (m.error) {
                  <p class="error">{{ m.error }}</p>
                }
                @if (m.sources.length) {
                  <ul class="citations" [attr.aria-label]="'chat.sources' | translate">
                    @for (source of m.sources; track $index) {
                      <li class="citation">
                        <app-icon name="document" [size]="16" />
                        <b>{{ source.id }}</b>
                        <span>{{ source.label }}</span>
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
                    @if (p.kind === 'answer') {
                      @if (currentClient.id(); as clientId) {
                        <div class="proposal-actions">
                          <a
                            class="btn-link"
                            [routerLink]="['/clients', clientId, 'profilierung']"
                            >{{ 'chat.reviewInQuestionnaire' | translate }}</a
                          >
                        </div>
                      }
                    } @else {
                      @switch (m.proposalState) {
                        @case ('applied') {
                          <div class="proposal-actions" role="status">
                            <app-badge tone="ok">{{ 'chat.applied' | translate }}</app-badge>
                            @if (currentClient.id(); as clientId) {
                              <a
                                class="btn-link"
                                [routerLink]="['/clients', clientId, 'beratung']"
                                >{{ 'chat.viewInAdvisory' | translate }}</a
                              >
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
                    }
                  </article>
                }
              </li>
            }
          }
        </ol>
      </div>
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
            #input
            id="chat-input"
            name="draft"
            rows="2"
            data-testid="chat-input"
            [(ngModel)]="draft"
            (keydown.enter)="onEnter($event)"
          ></textarea>
        </div>
        <button
          class="send"
          type="submit"
          data-testid="chat-send"
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
  private readonly input = viewChild.required<ElementRef<HTMLTextAreaElement>>('input');

  protected readonly suggestions = SUGGESTIONS;
  protected readonly messages = signal<readonly ChatMessage[]>([]);
  protected readonly busy = signal(false);
  protected draft = '';

  private pendingTokens = '';
  private flushTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.cancelFlush());
  }

  focusInput(): void {
    this.input().nativeElement.focus();
  }

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
      await this.api.chat(this.currentClient.id(), message, (event) => this.apply(event));
    } catch (error) {
      this.patchLast((m) => ({
        ...m,
        error: this.translate.instant('chat.failed', { detail: errorText(error) }),
      }));
    } finally {
      this.flushTokens();
      this.busy.set(false);
      this.scrollToEnd();
    }
  }

  protected sendSuggestion(key: string): void {
    void this.send(this.translate.instant(key));
  }

  protected async applyProposal(index: number, proposal: HedgeDraft): Promise<void> {
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

  private apply(event: ChatEvent): void {
    if (event.type === 'token') {
      this.bufferToken(event.text);
      return;
    }
    // Text streamed so far belongs before whatever this event adds.
    this.flushTokens();
    switch (event.type) {
      case 'citation':
        this.patchLast((m) => ({
          ...m,
          sources: [...m.sources, toSource(event.citation, m.sources.length)],
        }));
        break;
      case 'proposal':
        this.patchLast((m) => ({ ...m, proposal: event.proposal, proposalState: 'open' }));
        break;
      case 'error':
        this.patchLast((m) => ({ ...m, error: event.message }));
        break;
      case 'done':
        break;
    }
  }

  private bufferToken(text: string): void {
    this.pendingTokens += text;
    this.flushTimer ??= setTimeout(() => this.flushTokens(), RENDER_INTERVAL_MS);
  }

  private flushTokens(): void {
    this.cancelFlush();
    if (!this.pendingTokens) return;
    const chunk = this.pendingTokens;
    this.pendingTokens = '';
    const follow = this.isNearBottom();
    this.patchLast((m) => ({ ...m, text: m.text + chunk }));
    if (follow) this.scrollToEnd();
  }

  private cancelFlush(): void {
    if (this.flushTimer === null) return;
    clearTimeout(this.flushTimer);
    this.flushTimer = null;
  }

  private isNearBottom(): boolean {
    const element = this.body().nativeElement;
    return element.scrollHeight - element.scrollTop - element.clientHeight < FOLLOW_THRESHOLD_PX;
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
    sources: [],
    proposal: null,
    proposalState: 'open',
    error: null,
  };
}

/** Sources without a marker are numbered in arrival order, matching the agent's «[S1]», «[S2]», … */
function toSource(citation: Citation, index: number): ChatSource {
  return {
    id: citation.id ?? `S${index + 1}`,
    label: `${citation.document}, ${citation.location}`,
  };
}
