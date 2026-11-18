import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ChfPipe, MioPipe, PctPipe } from '../core/format';
import { Assessment, ChecklistItem } from '../core/models';
import { RefreshBus } from '../core/refresh-bus';
import { TreasuryApi } from '../core/treasury-api';
import { SUITABILITY_LABEL, errorText } from './labels';

@Component({
  selector: 'app-beratung-page',
  imports: [RouterLink, ChfPipe, MioPipe, PctPipe],
  template: `
    <div class="row between">
      <h1>Beratung</h1>
      <a class="btn btn-secondary" [routerLink]="['/clients', id()]">Zum Dashboard</a>
    </div>
    @if (error()) { <p class="error">{{ error() }}</p> }
    @if (assessment(); as a) {
      <div class="grid-2">
        <div class="stack">
          @for (h of a.hedges; track h.currency) {
            <section class="card">
              <div class="row between">
                <h2>Absicherung {{ h.currency }}</h2>
                <span class="muted small">Policy-Obergrenze {{ h.cap }} % · Art. 12 FIDLEG</span>
              </div>
              <p class="small">{{ h.action }}</p>
              <div class="tranches">
                @for (r of h.ratios; track $index) {
                  <div class="tranche">
                    <span class="muted small">Q{{ $index + 1 }}</span>
                    <div class="bar"><div class="fill" [style.height.%]="r"></div><div class="cap" [style.bottom.%]="h.cap"></div></div>
                    <strong>{{ r }} %</strong>
                  </div>
                }
              </div>
              <dl class="kv">
                <dt>Durchschnittliche Quote</dt><dd>{{ h.averageRatio | pct: false }}</dd>
                <dt>Abgesichertes Volumen</dt><dd>{{ h.currency }} {{ h.hedgedAmountFx | mio: 2 }}</dd>
                <dt>Zinsdifferenz (Carry)</dt><dd>{{ h.carryChf | chf }}</dd>
                @if (h.collarShare !== null) { <dt>Anteil Zero-Cost-Collar</dt><dd>{{ h.collarShare | pct }}</dd> }
                @if (h.scenarioUnhedgedChf !== null && h.scenarioHedgedChf !== null) {
                  <dt>Szenario {{ h.currency }} −10 %</dt><dd>ohne {{ h.scenarioUnhedgedChf | chf }} · mit {{ h.scenarioHedgedChf | chf }}</dd>
                }
              </dl>
              @if (h.limitedByPolicy) { <p class="notice warn small">Quote auf {{ h.cap }} % begrenzt (Treasury-Policy). Nie über 100 % der Nettoposition.</p> }
            </section>
          }
          <section class="card">
            <h2>Anlage der Überschussliquidität</h2>
            <p class="small">Überschuss {{ a.liquidity.excess | chf }}</p>
            <div class="split">
              @for (p of a.liquidity.parts; track p.product) {
                @if (p.share > 0) { <div class="split-part" [style.flex-grow]="p.share">{{ p.share }} %</div> }
              }
            </div>
            <table class="table"><tbody>
              @for (p of a.liquidity.parts; track p.product) {
                <tr><td>{{ p.product }}</td><td class="num">{{ p.share }} %</td><td class="num">{{ p.amount | chf }}</td></tr>
              }
            </tbody></table>
          </section>
        </div>

        <div class="stack">
          <section class="card">
            <h2>Produkte und Eignung</h2>
            <table class="table"><tbody>
              @for (s of a.suitability; track s.product) {
                <tr>
                  <td>{{ s.product }}@if (s.kidRequired) { <span class="tag">BIB</span> }</td>
                  <td>
                    <span class="pill" [class]="suitabilityLabel[s.suitability].css">{{ suitabilityLabel[s.suitability].text }}</span>
                    @if (s.note) { <div class="muted small">{{ s.note }}</div> }
                  </td>
                </tr>
              }
            </tbody></table>
          </section>
          <section class="card">
            <div class="row between"><h2>Vor Abschluss</h2><span class="muted small">{{ a.checklist.length }} Punkte</span></div>
            <ul class="checklist">
              @for (item of a.checklist; track item.id) {
                <li>
                  <label>
                    <input type="checkbox" [checked]="item.done" (change)="toggle(item, $any($event.target).checked)" />
                    <span>{{ item.text }} <small class="muted">{{ item.legalBasis }}</small></span>
                  </label>
                </li>
              }
            </ul>
          </section>
          <section class="card">
            <h2>Freigabe</h2>
            @if (a.approvalStatus === 'APPROVED') {
              <p class="notice ok">Vorschlag freigegeben. Jede Änderung hebt die Freigabe auf.</p>
            } @else {
              @if (blockers().length) {
                <ul class="blockers">
                  @for (b of blockers(); track b) { <li>{{ b }}</li> }
                </ul>
              }
              <button class="btn btn-primary" type="button" [disabled]="blockers().length > 0 || approving()" (click)="approve(a)">Vorschlag freigeben</button>
              <p class="muted small">Freigeben kann nur der Berater, nicht der Copilot.</p>
            }
          </section>
        </div>
      </div>
    } @else if (!error()) {
      <p class="muted">Beurteilung wird geladen …</p>
    }
  `
})
export class BeratungPage {
  readonly id = input.required<string>();

  private readonly api = inject(TreasuryApi);
  private readonly refresh = inject(RefreshBus);

  protected readonly suitabilityLabel = SUITABILITY_LABEL;
  protected readonly assessment = signal<Assessment | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly approving = signal(false);
  protected readonly blockers = computed(() => {
    const a = this.assessment();
    if (!a) return [];
    const open = a.checklist.filter((i) => !i.done).length;
    return [
      ...(a.unconfirmedAnswers ? [`${a.unconfirmedAnswers} vorbefüllte Antworten im Fragebogen bestätigen`] : []),
      ...(open ? [`${open} ${open === 1 ? 'Punkt' : 'Punkte'} der Checkliste erledigen`] : [])
    ];
  });

  constructor() {
    effect(() => {
      this.refresh.tick();
      void this.load(this.id());
    });
  }

  protected async toggle(item: ChecklistItem, done: boolean): Promise<void> {
    await this.run(() => this.api.setChecklistItem(this.id(), item.id, done));
  }

  protected async approve(assessment: Assessment): Promise<void> {
    this.approving.set(true);
    await this.run(() => this.api.approve(this.id(), assessment.version));
    this.approving.set(false);
  }

  private async run(action: () => Promise<void>): Promise<void> {
    try {
      await action();
      await this.load(this.id());
    } catch (e) {
      this.error.set(errorText(e));
    }
  }

  private async load(id: string): Promise<void> {
    try {
      this.assessment.set(await this.api.getAssessment(id));
      this.error.set(null);
    } catch (e) {
      this.error.set(`Beurteilung nicht verfügbar: ${errorText(e)}`);
    }
  }
}
