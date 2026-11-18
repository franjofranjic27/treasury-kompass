import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ChfPipe, MioPipe, PctPipe } from '../core/format';
import { Assessment, ClientDetail } from '../core/models';
import { RefreshBus } from '../core/refresh-bus';
import { TreasuryApi } from '../core/treasury-api';
import { DOCUMENT_STATUS_LABEL, RISK_LABEL, SUITABILITY_LABEL, errorText } from './labels';

@Component({
  selector: 'app-dashboard-page',
  imports: [RouterLink, MioPipe, PctPipe, ChfPipe],
  template: `
    @if (client(); as c) {
      <div class="row between">
        <div>
          <h1>{{ c.name }}</h1>
          <p class="muted">{{ c.shortLabel }} · {{ c.industry }} · {{ c.employees }}</p>
        </div>
        <div class="row">
          <a class="btn btn-secondary" [routerLink]="['/clients', c.id, 'profilierung']">Profilierung</a>
          <a class="btn btn-primary" [routerLink]="['/clients', c.id, 'beratung']">Zur Beratung</a>
        </div>
      </div>
      <div class="grid-2">
        <div class="stack">
          <section class="card">
            <h2>Stammdaten</h2>
            <dl class="kv">
              <dt>Firma</dt><dd>{{ c.name }}</dd>
              <dt>Branche</dt><dd>{{ c.industry }}</dd>
              <dt>Grösse</dt><dd>{{ c.employees }}</dd>
              <dt>Freigabe</dt><dd>{{ c.approvalStatus === 'APPROVED' ? 'Freigegeben' : 'Nicht freigegeben' }}</dd>
            </dl>
          </section>
          <section class="card">
            <h2>Kennzahlen</h2>
            <table class="table">
              <tbody>
                @for (f of c.figures; track f.key) {
                  <tr><td>{{ f.label }}</td><td class="num">CHF {{ f.value | mio }}</td><td class="src">{{ f.source ?? '–' }}</td></tr>
                }
              </tbody>
            </table>
          </section>
          <section class="card">
            <h2>FX-Flüsse, nächste 12 Monate</h2>
            <table class="table">
              <thead><tr><th>Währung</th><th class="num">Zuflüsse</th><th class="num">Abflüsse</th><th>Fundstelle</th></tr></thead>
              <tbody>
                @for (fx of c.fxFlows; track fx.currency) {
                  <tr><td>{{ fx.currency }}</td><td class="num">{{ fx.inflow | mio }}</td><td class="num">{{ fx.outflow | mio }}</td><td class="src">{{ fx.source ?? '–' }}</td></tr>
                } @empty {
                  <tr><td colspan="4" class="muted">Keine Fremdwährungsflüsse erfasst.</td></tr>
                }
              </tbody>
            </table>
          </section>
          @if (assessment(); as a) {
            <section class="card">
              <h2>Nettopositionen</h2>
              <table class="table">
                <thead><tr><th>Währung</th><th class="num">Netto</th><th class="num">in CHF</th><th class="num">% EBIT bei −10 %</th><th></th></tr></thead>
                <tbody>
                  @for (e of a.exposures; track e.currency) {
                    <tr>
                      <td>{{ e.currency }} <span class="muted small">{{ e.direction }}</span></td>
                      <td class="num">{{ e.netPosition | mio }}</td>
                      <td class="num">{{ e.netPositionChf | chf }}</td>
                      <td class="num">{{ e.sensitivityPctEbit | pct: true : 1 }}</td>
                      <td><span class="pill" [class]="riskLabel[e.riskLevel].css">{{ riskLabel[e.riskLevel].text }}</span></td>
                    </tr>
                  }
                </tbody>
              </table>
            </section>
            <section class="card">
              <h2>Liquidität</h2>
              <dl class="kv">
                <dt>Flüssige Mittel</dt><dd>{{ cash() | chf }}</dd>
                <dt>Mindestliquidität</dt><dd>{{ c.minimumLiquidity | chf }}</dd>
                <dt>Überschussliquidität</dt><dd><strong>{{ a.liquidity.excess | chf }}</strong></dd>
              </dl>
            </section>
          }
          <section class="card">
            <h2>Dokumente</h2>
            <ul class="plain">
              @for (d of c.documents; track d.id) {
                <li class="row between"><span>{{ d.fileName }}</span><span class="pill" [class.ok]="d.status === 'EXTRACTED'">{{ docStatus[d.status] }}</span></li>
              } @empty {
                <li class="muted">Keine Dokumente. <a routerLink="/">Unterlagen hochladen</a></li>
              }
            </ul>
          </section>
        </div>

        <div class="stack">
          @if (assessment(); as a) {
            <section class="card">
              <div class="row between"><h2>FIDLEG-Risikoprofil</h2>
                <span class="pill" [class.ok]="a.approvalStatus === 'APPROVED'">{{ a.approvalStatus === 'APPROVED' ? 'Freigegeben' : a.unconfirmedAnswers + ' Antworten offen' }}</span>
              </div>
              <div class="profile-hero">
                <span class="score">{{ a.profile.score }}</span>
                <div><strong>{{ a.profile.label }}</strong><p class="muted small">Risikoprofil = min(Fähigkeit, Bereitschaft)</p></div>
              </div>
            </section>
            <section class="card">
              <h2>Kundensegment</h2>
              <p><strong>{{ a.segment.label }}</strong></p>
              <p class="small">{{ a.segment.reason }}</p>
              <p class="muted small">Rechtsgrundlage: {{ a.segment.legalBasis }} · {{ a.segment.thresholdsExceeded }} von 3 Schwellenwerten</p>
            </section>
            <section class="card">
              <div class="row between"><h2>Risikofähigkeit</h2><span class="score-sm">{{ a.capacity.score }}</span></div>
              <table class="table"><tbody>
                @for (f of a.capacity.factors; track f.label) { <tr><td>{{ f.label }}</td><td>{{ f.value }}</td><td class="num">{{ f.score }}</td></tr> }
              </tbody></table>
            </section>
            <section class="card">
              <div class="row between"><h2>Risikobereitschaft</h2><span class="score-sm">{{ a.willingness.score }}</span></div>
              <table class="table"><tbody>
                @for (f of a.willingness.factors; track f.label) { <tr><td>{{ f.label }}</td><td>{{ f.value }}</td><td class="num">{{ f.score }}</td></tr> }
              </tbody></table>
            </section>
            <section class="card">
              <h2>Kenntnisse und Eignung</h2>
              <p class="muted small">Art. 12 und 14 FIDLEG</p>
              <table class="table"><tbody>
                @for (s of a.suitability; track s.product) {
                  <tr>
                    <td>{{ s.product }}@if (s.kidRequired) { <span class="tag">BIB</span> }</td>
                    <td class="small">{{ s.knowledge }}</td>
                    <td><span class="pill" [class]="suitabilityLabel[s.suitability].css">{{ suitabilityLabel[s.suitability].text }}</span></td>
                  </tr>
                }
              </tbody></table>
            </section>
          } @else {
            <section class="card"><h2>FIDLEG-Risikoprofil</h2><p class="muted">{{ assessmentError() ?? 'Wird geladen …' }}</p></section>
          }
        </div>
      </div>
    } @else {
      <p [class.error]="error()">{{ error() ?? 'Kunde wird geladen …' }}</p>
    }
  `
})
export class DashboardPage {
  readonly id = input.required<string>();

  private readonly api = inject(TreasuryApi);
  private readonly refresh = inject(RefreshBus);

  protected readonly docStatus = DOCUMENT_STATUS_LABEL;
  protected readonly riskLabel = RISK_LABEL;
  protected readonly suitabilityLabel = SUITABILITY_LABEL;
  protected readonly client = signal<ClientDetail | null>(null);
  protected readonly assessment = signal<Assessment | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly assessmentError = signal<string | null>(null);

  constructor() {
    effect(() => {
      this.refresh.tick();
      void this.load(this.id());
    });
  }

  protected cash(): number | null {
    return this.client()?.figures.find((f) => f.key === 'fin.cash')?.value ?? null;
  }

  private async load(id: string): Promise<void> {
    try {
      this.client.set(await this.api.getClient(id));
    } catch (e) {
      this.error.set(`Kunde konnte nicht geladen werden: ${errorText(e)}`);
      return;
    }
    try {
      this.assessment.set(await this.api.getAssessment(id));
      this.assessmentError.set(null);
    } catch (e) {
      this.assessment.set(null);
      this.assessmentError.set(errorText(e));
    }
  }
}
