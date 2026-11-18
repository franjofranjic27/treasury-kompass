import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ClientDocument, ClientSummary } from '../core/models';
import { TreasuryApi } from '../core/treasury-api';
import { DOCUMENT_STATUS_LABEL, DOCUMENT_STEPS, errorText } from './labels';

@Component({
  selector: 'app-start-page',
  imports: [FormsModule, RouterLink],
  template: `
    <h1>Kunden</h1>
    <div class="grid-2">
      <section class="card">
        <div class="row">
          <input class="input grow" type="search" placeholder="Kunde suchen …" [ngModel]="query()" (ngModelChange)="query.set($event)" />
          <button class="btn btn-secondary" type="button" (click)="showCreate.set(!showCreate())">Neuer Kunde</button>
        </div>
        @if (showCreate()) {
          <form class="row mt" (ngSubmit)="create()">
            <input class="input grow" name="newName" placeholder="Firmenname, z.B. Neu AG" [(ngModel)]="newName" required />
            <button class="btn btn-primary" type="submit" [disabled]="!newName.trim()">Anlegen</button>
          </form>
        }
        @if (error()) { <p class="error">{{ error() }}</p> }
        <ul class="client-list">
          @for (c of clients(); track c.id) {
            <li>
              <button type="button" class="client" [class.selected]="selected()?.id === c.id" (click)="select(c)">
                <strong>{{ c.name }}</strong>
                <span class="muted small">{{ c.shortLabel }} · {{ c.industry }} · {{ c.employees }}</span>
                <span class="small">
                  {{ c.segment ?? 'Segment offen' }} · {{ c.riskProfile ?? 'Profil offen' }} · {{ c.documentCount }} Dok. ·
                  {{ c.openItems }} offene Punkte ·
                  <span class="pill" [class.ok]="c.approvalStatus === 'APPROVED'">{{ c.approvalStatus === 'APPROVED' ? 'Freigegeben' : 'Nicht freigegeben' }}</span>
                </span>
              </button>
            </li>
          } @empty {
            <li class="muted">Keine Kunden gefunden.</li>
          }
        </ul>
      </section>

      <section class="card">
        @if (selected(); as client) {
          <h2>{{ client.name }}</h2>
          <label class="dropzone" [class.drag]="dragging()" (dragover)="onDragOver($event)" (dragleave)="dragging.set(false)" (drop)="onDrop($event)">
            <input type="file" accept="application/pdf" multiple hidden (change)="onFileInput($event)" />
            <strong>PDF hier ablegen</strong>
            <span class="muted small">oder klicken, um Jahresrechnung oder Geschäftsbericht auszuwählen</span>
          </label>
          <ul class="doc-list">
            @for (d of documents(); track d.id) {
              <li>
                <div class="row between"><strong class="small">{{ d.fileName }}</strong><span class="muted small">{{ d.pageCount ? d.pageCount + ' Seiten' : '' }}</span></div>
                @if (d.status === 'FAILED') {
                  <span class="pill crit">Fehlgeschlagen</span>
                } @else {
                  <ol class="steps">
                    @for (step of steps; track step) {
                      <li [class.done]="stepIndex(d) >= $index">{{ statusLabel[step] }}</li>
                    }
                  </ol>
                }
              </li>
            } @empty {
              <li class="muted small">Noch keine Unterlagen.</li>
            }
          </ul>
          <div class="row mt">
            <a class="btn btn-primary" [routerLink]="['/clients', client.id, 'profilierung']">Zur Profilierung</a>
            <a class="btn btn-secondary" [routerLink]="['/clients', client.id]">Zum Dashboard</a>
          </div>
        } @else {
          <p class="muted">Kunde auswählen, um Unterlagen hochzuladen.</p>
        }
      </section>
    </div>
  `
})
export class StartPage {
  private readonly api = inject(TreasuryApi);

  protected readonly steps = DOCUMENT_STEPS;
  protected readonly statusLabel = DOCUMENT_STATUS_LABEL;
  protected readonly query = signal('');
  protected readonly clients = signal<readonly ClientSummary[]>([]);
  protected readonly selected = signal<ClientSummary | null>(null);
  protected readonly documents = signal<readonly ClientDocument[]>([]);
  protected readonly showCreate = signal(false);
  protected readonly dragging = signal(false);
  protected readonly error = signal<string | null>(null);
  protected newName = '';

  constructor() {
    effect(() => void this.loadClients(this.query()));
  }

  protected async select(client: ClientSummary): Promise<void> {
    this.selected.set(client);
    this.documents.set([]);
    try {
      this.documents.set((await this.api.getClient(client.id)).documents);
    } catch (e) {
      this.error.set(errorText(e));
    }
  }

  protected async create(): Promise<void> {
    try {
      const client = await this.api.createClient(this.newName);
      this.newName = '';
      this.showCreate.set(false);
      await this.loadClients(this.query());
      await this.select(client);
    } catch (e) {
      this.error.set(errorText(e));
    }
  }

  protected stepIndex(document: ClientDocument): number {
    return DOCUMENT_STEPS.indexOf(document.status);
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    this.upload(Array.from(event.dataTransfer?.files ?? []));
  }

  protected onFileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.upload(Array.from(input.files ?? []));
    input.value = '';
  }

  private upload(files: readonly File[]): void {
    const client = this.selected();
    if (!client) return;
    const pdfs = files.filter((f) => f.type === 'application/pdf' || f.name.toLowerCase().endsWith('.pdf'));
    if (pdfs.length !== files.length) this.error.set('Nur PDF-Dateien werden unterstützt.');
    for (const file of pdfs) {
      this.api
        .uploadDocument(client.id, file, (doc) => this.documents.update((list) => [...list.filter((d) => d.id !== doc.id), doc]))
        .then(() => this.loadClients(this.query()))
        .catch((e) => this.error.set(`Upload fehlgeschlagen: ${errorText(e)}`));
    }
  }

  private async loadClients(query: string): Promise<void> {
    try {
      this.clients.set(await this.api.listClients(query));
      this.error.set(null);
    } catch (e) {
      this.error.set(`Kunden konnten nicht geladen werden: ${errorText(e)}`);
    }
  }
}
