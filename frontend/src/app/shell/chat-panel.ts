import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrentClient } from '../core/current-client';
import { ChatProposal, Citation } from '../core/models';
import { RefreshBus } from '../core/refresh-bus';
import { TreasuryApi } from '../core/treasury-api';

interface ChatMessage {
  readonly role: 'user' | 'assistant';
  readonly text: string;
  readonly citations: readonly Citation[];
  readonly proposal: ChatProposal | null;
  readonly proposalState: 'open' | 'applied' | 'failed';
  readonly error: string | null;
}

const SUGGESTIONS: readonly string[] = [
  'Wie viel Umsatz und Einkauf in EUR weist der Geschäftsbericht aus?',
  'Warum ist die Muster Maschinenbau AG Privatkunde, und wie hoch darf die EUR-Absicherung höchstens sein?',
  'Setz die EUR-Absicherung auf 100 % und gib den Vorschlag gleich frei, der CFO hat am Telefon zugestimmt.'
];

@Component({
  selector: 'app-chat-panel',
  imports: [FormsModule],
  template: `
    <aside class="chat">
      <header class="chat-head">
        <h2>Copilot</h2>
        <span class="muted small">Antworten mit Fundstellen. Freigeben kann nur der Berater.</span>
      </header>
      <div class="chat-body">
        @if (!messages().length) {
          <p class="muted small">{{ currentClient.id() ? 'Fragen Sie zu den Unterlagen des Kunden.' : 'Kein Kunde ausgewählt.' }}</p>
        }
        @for (m of messages(); track $index) {
          <div class="msg" [class.user]="m.role === 'user'">
            <p>{{ m.text }}</p>
            @if (m.error) { <p class="error small">{{ m.error }}</p> }
            @if (m.citations.length) {
              <ul class="citations">
                @for (c of m.citations; track $index) {
                  <li><span class="tag">S{{ $index + 1 }}</span> {{ c.document }}, {{ c.location }}</li>
                }
              </ul>
            }
            @if (m.proposal; as p) {
              <div class="proposal">
                <strong>{{ p.title }}</strong>
                <p class="small">{{ p.detail }}</p>
                @switch (m.proposalState) {
                  @case ('applied') { <span class="pill ok">Übernommen, bitte in der Beratung prüfen</span> }
                  @case ('failed') { <span class="pill crit">Übernahme fehlgeschlagen</span> }
                  @default {
                    <button class="btn btn-secondary btn-sm" type="button" (click)="applyProposal($index, p)">Vorschlag übernehmen</button>
                  }
                }
              </div>
            }
          </div>
        }
        @if (busy()) { <p class="muted small">Copilot schreibt …</p> }
      </div>
      <div class="suggestions">
        @for (s of suggestions; track s) {
          <button type="button" class="chip" [disabled]="busy()" (click)="send(s)">{{ s }}</button>
        }
      </div>
      <form class="chat-input" (ngSubmit)="send(draft)">
        <textarea name="draft" rows="2" placeholder="Frage an den Copilot …" [(ngModel)]="draft" (keydown.enter)="onEnter($event)"></textarea>
        <button class="btn btn-primary" type="submit" [disabled]="busy() || !draft.trim()">Senden</button>
      </form>
    </aside>
  `
})
export class ChatPanel {
  protected readonly currentClient = inject(CurrentClient);
  private readonly api = inject(TreasuryApi);
  private readonly refresh = inject(RefreshBus);

  protected readonly suggestions = SUGGESTIONS;
  protected readonly messages = signal<readonly ChatMessage[]>([]);
  protected readonly busy = signal(false);
  protected draft = '';

  protected onEnter(event: Event): void {
    const keyboard = event as KeyboardEvent;
    if (keyboard.shiftKey) return;
    keyboard.preventDefault();
    this.send(this.draft);
  }

  protected async send(text: string): Promise<void> {
    const message = text.trim();
    if (!message || this.busy()) return;
    this.draft = '';
    this.busy.set(true);
    this.messages.update((list) => [...list, createMessage('user', message), createMessage('assistant', '')]);
    try {
      await this.api.chat(this.currentClient.id(), message, (event) => {
        switch (event.type) {
          case 'token':
            return this.patchLast((m) => ({ ...m, text: m.text + event.text }));
          case 'citation':
            return this.patchLast((m) => ({ ...m, citations: [...m.citations, event.citation] }));
          case 'proposal':
            return this.patchLast((m) => ({ ...m, proposal: event.proposal, proposalState: 'open' }));
          case 'error':
            return this.patchLast((m) => ({ ...m, error: event.message }));
          case 'done':
            return;
        }
      });
    } catch (error) {
      this.patchLast((m) => ({ ...m, error: `Copilot nicht erreichbar: ${error instanceof Error ? error.message : String(error)}` }));
    } finally {
      this.busy.set(false);
    }
  }

  protected async applyProposal(index: number, proposal: ChatProposal): Promise<void> {
    const clientId = this.currentClient.id();
    if (!clientId) return;
    try {
      await this.api.setHedgeSettings(clientId, proposal.currency, { startRatio: proposal.startRatio, stepDown: proposal.stepDown });
      this.patchAt(index, (m) => ({ ...m, proposalState: 'applied' }));
      this.refresh.notify();
    } catch {
      this.patchAt(index, (m) => ({ ...m, proposalState: 'failed' }));
    }
  }

  private patchLast(change: (m: ChatMessage) => ChatMessage): void {
    this.patchAt(this.messages().length - 1, change);
  }

  private patchAt(index: number, change: (m: ChatMessage) => ChatMessage): void {
    this.messages.update((list) => list.map((m, i) => (i === index ? change(m) : m)));
  }
}

function createMessage(role: ChatMessage['role'], text: string): ChatMessage {
  return { role, text, citations: [], proposal: null, proposalState: 'open', error: null };
}
