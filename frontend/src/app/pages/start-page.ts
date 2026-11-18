import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ClientDocument, ClientSummary } from '../core/models';
import { RefreshBus } from '../core/refresh-bus';
import { TreasuryApi } from '../core/treasury-api';
import { Badge } from '../shared/badge';
import { Icon } from '../shared/icon';
import { CLIENT_STATUS_TONE, DOCUMENT_STEPS, clientStatus, errorText, plural } from './labels';

type StageState = 'done' | 'running' | 'open';

@Component({
  selector: 'app-start-page',
  imports: [FormsModule, RouterLink, TranslatePipe, Badge, Icon],
  template: `
    <div class="page-head">
      <div class="page-title">
        <h1>{{ 'start.title' | translate }}</h1>
        <p class="lead">{{ 'start.lead' | translate }}</p>
      </div>
      <button
        class="btn btn-primary"
        type="button"
        [attr.aria-expanded]="showCreate()"
        aria-controls="new-client"
        (click)="showCreate.set(!showCreate())"
      >
        <app-icon name="plus" />{{ 'start.newClient' | translate }}
      </button>
    </div>

    @if (showCreate()) {
      <form
        id="new-client"
        class="card form-inline"
        [attr.aria-label]="'start.createLabel' | translate"
        (ngSubmit)="create()"
      >
        <div class="field grow">
          <label for="new-name">{{ 'start.companyName' | translate }}</label>
          <input
            id="new-name"
            name="newName"
            autocomplete="organization"
            required
            [(ngModel)]="newName"
          />
        </div>
        <div class="actions">
          <button class="btn btn-primary" type="submit" [disabled]="!newName.trim()">
            {{ 'start.create' | translate }}
          </button>
          <button class="btn-link" type="button" (click)="showCreate.set(false)">
            {{ 'common.cancel' | translate }}
          </button>
        </div>
      </form>
    }

    <div class="toolbar">
      <div class="field field-search">
        <div class="field-body">
          <label for="search">{{ 'start.search' | translate }}</label>
          <input
            id="search"
            type="search"
            [ngModel]="query()"
            (ngModelChange)="query.set($event)"
          />
        </div>
        <app-icon name="search" />
      </div>
      <p class="meta-lg" role="status">
        {{ plural('start.count', clients().length) | translate: { count: clients().length } }}
      </p>
    </div>

    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }

    <section class="client-list" aria-labelledby="list-title">
      <h2 id="list-title" class="sr-only">{{ 'start.listTitle' | translate }}</h2>
      <div class="client-row client-head" aria-hidden="true">
        <span class="col-name">{{ 'start.colClient' | translate }}</span>
        <span class="col-seg">{{ 'start.colSegment' | translate }}</span>
        <span class="col-status">{{ 'start.colProfiling' | translate }}</span>
        <span class="col-docs">{{ 'start.colDocuments' | translate }}</span>
        <span class="col-mark"></span>
      </div>
      <ul>
        @for (c of clients(); track c.id) {
          @let isSelected = selectedId() === c.id;
          @let status = clientStatus(c);
          <li>
            <button
              type="button"
              class="client-row"
              [class.selected]="isSelected"
              [attr.aria-pressed]="isSelected"
              (click)="select(c.id)"
            >
              <span class="col-name">
                <b>{{ c.name }}</b>
                <span class="meta">
                  {{ c.industry ?? ('common.dash' | translate) }}
                  @if (c.employees !== null) {
                    ·
                    {{
                      plural('client.employees', c.employees) | translate: { count: c.employees }
                    }}
                  }
                </span>
              </span>
              <span class="col-seg">
                <span class="sr-only">{{ 'start.colSegment' | translate }}: </span>
                @if (c.segment) {
                  <span>{{ 'segment.' + c.segment | translate }}</span>
                  <span class="meta">{{
                    c.riskProfile
                      ? ('start.profileNote'
                        | translate: { profile: ('riskProfile.' + c.riskProfile | translate) })
                      : ('start.segmentRule' | translate)
                  }}</span>
                } @else {
                  <span>{{ 'start.segmentOpen' | translate }}</span>
                  <span class="meta">{{ 'start.segmentOpenNote' | translate }}</span>
                }
              </span>
              <span class="col-status">
                <span class="sr-only">{{ 'start.colProfiling' | translate }}: </span>
                <app-badge [tone]="statusTone[status]">{{
                  'clientStatus.' + status | translate
                }}</app-badge>
                <span class="meta">{{
                  c.documentCount === 0
                    ? ('start.noDocuments' | translate)
                    : (plural('start.openItems', c.openItems) | translate: { count: c.openItems })
                }}</span>
              </span>
              <span class="col-docs">
                <span class="sr-only">{{ 'start.colDocuments' | translate }}: </span>
                {{
                  plural('start.documentCount', c.documentCount)
                    | translate: { count: c.documentCount }
                }}
              </span>
              <span class="col-mark">
                @if (isSelected) {
                  <app-icon name="check" /><span>{{ 'start.selected' | translate }}</span>
                } @else {
                  <app-icon name="chevron-right" />
                }
              </span>
            </button>
          </li>
        }
      </ul>
      @if (!clients().length) {
        <div class="empty-state">
          <p class="strong">{{ 'start.emptyTitle' | translate }}</p>
          <p class="meta-lg">{{ 'start.emptyText' | translate }}</p>
        </div>
      }
    </section>

    @if (selected(); as client) {
      <section class="card card-roomy" aria-labelledby="docs-title">
        <div class="card-title">
          <span class="eyebrow">{{ 'start.documents' | translate }}</span>
          <h2 id="docs-title">{{ client.name }}</h2>
        </div>

        <div
          class="dropzone"
          [class.drag]="dragging()"
          (dragover)="onDragOver($event)"
          (dragleave)="dragging.set(false)"
          (drop)="onDrop($event)"
        >
          <app-icon name="upload" [size]="32" [strokeWidth]="1.5" />
          <div class="dropzone-text">
            <p class="strong">{{ 'start.dropTitle' | translate }}</p>
            <p class="meta-lg">{{ 'start.dropText' | translate }}</p>
          </div>
          <input
            id="upload"
            class="sr-only file-input"
            type="file"
            accept="application/pdf,.pdf"
            multiple
            (change)="onFileInput($event)"
          />
          <label for="upload" class="btn btn-secondary">{{ 'start.chooseFile' | translate }}</label>
        </div>

        <ul class="doc-list" [attr.aria-label]="'start.uploadedDocuments' | translate">
          @for (d of documents(); track d.id) {
            <li class="doc">
              <div class="doc-file">
                <app-icon name="file" [size]="32" [strokeWidth]="1.5" />
                <div class="doc-text">
                  <b>{{ d.fileName }}</b>
                  <span class="meta">{{
                    d.pageCount
                      ? (plural('start.pages', d.pageCount) | translate: { count: d.pageCount })
                      : ('start.justUploaded' | translate)
                  }}</span>
                </div>
              </div>
              @if (d.status === 'FAILED') {
                <app-badge tone="crit">{{ 'documentStatus.FAILED' | translate }}</app-badge>
              } @else {
                <ol
                  class="stages"
                  [attr.aria-label]="'start.processing' | translate: { name: d.fileName }"
                >
                  @for (step of steps; track step) {
                    @let state = stageState(d, $index);
                    <li [attr.data-state]="state">
                      <span class="stage-bar" aria-hidden="true"></span>
                      <span class="stage-label">
                        @if (state === 'done') {
                          <app-icon name="check" [size]="16" />
                        } @else if (state === 'running') {
                          <app-icon name="progress" [size]="16" />
                        }
                        <span
                          >{{ 'documentStatus.' + step | translate
                          }}<span class="sr-only"
                            >, {{ 'start.stage.' + state | translate }}</span
                          ></span
                        >
                      </span>
                    </li>
                  }
                </ol>
              }
            </li>
          } @empty {
            <li class="doc-empty meta-lg">{{ 'start.noDocumentsYet' | translate }}</li>
          }
        </ul>

        <div class="next-row">
          <p>{{ nextNote(client) | translate: { count: client.openItems } }}</p>
          <div class="actions">
            <a class="btn btn-secondary" [routerLink]="['/clients', client.id]">{{
              'common.toDashboard' | translate
            }}</a>
            <a class="btn btn-primary" [routerLink]="['/clients', client.id, 'profilierung']"
              >{{ 'start.startProfiling' | translate }}<app-icon name="chevron-right"
            /></a>
          </div>
        </div>
      </section>
    } @else if (clients().length) {
      <p class="meta-lg">{{ 'start.selectHint' | translate }}</p>
    }
  `,
})
export class StartPage {
  /** Preselects a client, e.g. from «Unterlagen verwalten» on the dashboard (`/?client=<id>`). */
  readonly client = input<string>();

  private readonly api = inject(TreasuryApi);
  private readonly refresh = inject(RefreshBus);
  private readonly translate = inject(TranslateService);

  protected readonly steps = DOCUMENT_STEPS;
  protected readonly statusTone = CLIENT_STATUS_TONE;
  protected readonly clientStatus = clientStatus;
  protected readonly plural = plural;
  protected readonly query = signal('');
  protected readonly clients = signal<readonly ClientSummary[]>([]);
  protected readonly selectedId = signal<string | null>(null);
  protected readonly selected = computed(
    () => this.clients().find((c) => c.id === this.selectedId()) ?? null,
  );
  protected readonly documents = signal<readonly ClientDocument[]>([]);
  protected readonly showCreate = signal(false);
  protected readonly dragging = signal(false);
  protected readonly error = signal<string | null>(null);
  protected newName = '';

  constructor() {
    effect(() => {
      this.refresh.tick();
      void this.loadClients(this.query());
    });
    effect(() => {
      const preselect = this.client();
      if (preselect) untracked(() => void this.select(preselect));
    });
  }

  protected async select(clientId: string): Promise<void> {
    this.selectedId.set(clientId);
    this.documents.set([]);
    try {
      this.documents.set((await this.api.getClient(clientId)).documents);
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
      await this.select(client.id);
    } catch (e) {
      this.error.set(errorText(e));
    }
  }

  protected stageState(document: ClientDocument, index: number): StageState {
    const reached = DOCUMENT_STEPS.indexOf(document.status);
    if (index <= reached) return 'done';
    return index === reached + 1 ? 'running' : 'open';
  }

  protected nextNote(client: ClientSummary): string {
    if (this.documents().length === 0) return 'start.nextNoDocuments';
    if (client.approvalStatus === 'APPROVED') return 'start.nextApproved';
    return plural('start.nextOpen', client.openItems);
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
    const clientId = this.selectedId();
    if (!clientId) return;
    const pdfs = files.filter(
      (f) => f.type === 'application/pdf' || f.name.toLowerCase().endsWith('.pdf'),
    );
    if (pdfs.length !== files.length) this.error.set(this.translate.instant('start.onlyPdf'));
    for (const file of pdfs) {
      this.api
        .uploadDocument(clientId, file, (doc) =>
          this.documents.update((list) => [...list.filter((d) => d.id !== doc.id), doc]),
        )
        .then(() => this.loadClients(this.query()))
        .catch((e) =>
          this.error.set(this.translate.instant('start.uploadFailed', { detail: errorText(e) })),
        );
    }
  }

  private async loadClients(query: string): Promise<void> {
    try {
      this.clients.set(await this.api.listClients(query));
      this.error.set(null);
    } catch (e) {
      this.error.set(this.translate.instant('start.loadFailed', { detail: errorText(e) }));
    }
  }
}
