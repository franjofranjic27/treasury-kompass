import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { DataMode } from '../core/data-mode';
import { ChfPipe, MioPipe, NumPipe, PctPipe } from '../core/format';
import { Assessment, ClientDetail, Exposure, FxFlow, KeyFigure } from '../core/models';
import { RefreshBus } from '../core/refresh-bus';
import { TreasuryApi } from '../core/treasury-api';
import { Badge } from '../shared/badge';
import { CaseNav } from '../shared/case-nav';
import { Icon } from '../shared/icon';
import { FactorList } from '../shared/score';
import { ScrollRegion } from '../shared/scroll-region';
import { DOCUMENT_TONE, RISK_TONE, SUITABILITY_TONE, inlineError, plural } from './labels';

interface CurrencyBlock {
  readonly flow: FxFlow;
  readonly exposure: Exposure | null;
}

@Component({
  selector: 'app-dashboard-page',
  imports: [
    RouterLink,
    TranslatePipe,
    MioPipe,
    NumPipe,
    PctPipe,
    ChfPipe,
    Badge,
    CaseNav,
    Icon,
    FactorList,
    ScrollRegion,
  ],
  styleUrl: './dashboard-page.scss',
  template: `
    <app-case-nav [clientId]="id()" />
    @if (client(); as c) {
      <div class="page-head">
        <div class="page-title">
          <span class="eyebrow">
            {{ 'dashboard.eyebrow' | translate }}
            @if (c.shortLabel) {
              · {{ c.shortLabel }}
            }
          </span>
          <h1>{{ c.name }}</h1>
          <p class="lead">
            {{ c.industry ?? ('common.dash' | translate) }}
            @if (c.employees !== null) {
              · {{ plural('client.employees', c.employees) | translate: { count: c.employees } }}
            }
          </p>
        </div>
        <div class="actions">
          <a class="btn btn-secondary" [routerLink]="['/clients', c.id, 'profilierung']">{{
            'dashboard.editProfiling' | translate
          }}</a>
          <a class="btn btn-primary" [routerLink]="['/clients', c.id, 'beratung']"
            >{{ 'dashboard.toAdvisory' | translate }}<app-icon name="chevron-right"
          /></a>
        </div>
      </div>

      <div class="columns">
        <div class="column">
          <h2>{{ 'dashboard.clientInfo' | translate }}</h2>

          <section class="card" aria-labelledby="master-title">
            <h3 id="master-title">{{ 'dashboard.masterData' | translate }}</h3>
            <dl class="facts">
              <div>
                <dt>{{ 'dashboard.industry' | translate }}</dt>
                <dd>{{ c.industry ?? ('common.dash' | translate) }}</dd>
              </div>
              <div>
                <dt>{{ 'dashboard.employees' | translate }}</dt>
                <dd>{{ c.employees ?? ('common.dash' | translate) }}</dd>
              </div>
              <div>
                <dt>{{ 'dashboard.clientType' | translate }}</dt>
                <dd>{{ c.shortLabel ?? ('common.dash' | translate) }}</dd>
              </div>
              <div>
                <dt>{{ 'common.approval' | translate }}</dt>
                <dd>{{ 'approval.' + c.approvalStatus | translate }}</dd>
              </div>
            </dl>
          </section>

          <section class="card" aria-labelledby="figures-title">
            <div class="card-title">
              <h3 id="figures-title">{{ 'dashboard.keyFigures' | translate }}</h3>
              <span class="meta">{{ 'dashboard.keyFiguresNote' | translate }}</span>
            </div>
            <div class="table-wrap" [appScrollRegion]="'dashboard.keyFigures' | translate">
              <table class="table">
                <thead>
                  <tr>
                    <th scope="col">{{ 'dashboard.keyFigure' | translate }}</th>
                    <th scope="col" class="num">{{ 'dashboard.value' | translate }}</th>
                    <th scope="col">{{ 'common.citation' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (f of c.figures; track f.key) {
                    <tr>
                      <td>{{ 'figure.' + f.key | translate }}</td>
                      <td class="num strong">{{ f.value | num: 1 }}</td>
                      <td class="src">{{ f.source ?? ('common.dash' | translate) }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </section>

          <section class="card" aria-labelledby="fx-title">
            <div class="card-title">
              <h3 id="fx-title">{{ 'dashboard.fxTitle' | translate }}</h3>
              <span class="meta">{{ 'dashboard.fxNote' | translate }}</span>
            </div>
            @for (block of currencyBlocks(); track block.flow.currency) {
              @let fx = block.flow;
              @let e = block.exposure;
              <div class="currency-block">
                <div class="currency-head">
                  <span class="currency-code">{{ fx.currency }}</span>
                  @if (e) {
                    <app-badge [tone]="riskTone[e.riskLevel]">{{
                      'risk.' + e.riskLevel | translate
                    }}</app-badge>
                  }
                </div>
                <dl class="facts">
                  <div>
                    <dt>{{ 'dashboard.inOut' | translate }}</dt>
                    <dd>{{ fx.currency }} {{ fx.inflow | mio }} · {{ fx.outflow | mio }}</dd>
                  </div>
                  @if (e) {
                    <div>
                      <dt>{{ 'direction.' + e.direction | translate }}</dt>
                      <dd class="strong">
                        {{ fx.currency }} {{ e.netPosition | mio: 1 : true }} (CHF
                        {{ e.netPositionChf | mio: 2 }})
                      </dd>
                    </div>
                    <div>
                      <dt>
                        {{
                          (e.direction === 'NET_OUTFLOW'
                            ? 'dashboard.shockUp'
                            : 'dashboard.shockDown'
                          ) | translate: { currency: fx.currency }
                        }}
                      </dt>
                      <dd class="strong">
                        {{
                          'dashboard.ebitImpact'
                            | translate
                              : {
                                  amount: (e.sensitivityChf | mio: 2),
                                  pct: (e.sensitivityPctEbit | pct),
                                }
                        }}
                      </dd>
                    </div>
                  }
                  <div>
                    <dt>{{ 'common.citation' | translate }}</dt>
                    <dd>{{ fx.source ?? ('common.dash' | translate) }}</dd>
                  </div>
                </dl>
              </div>
            } @empty {
              <p class="meta-lg">{{ 'dashboard.noFx' | translate }}</p>
            }
            <p class="meta">{{ 'dashboard.riskThresholds' | translate }}</p>
          </section>

          @if (assessment(); as a) {
            <section class="card" aria-labelledby="liq-title">
              <h3 id="liq-title">{{ 'dashboard.liquidityTitle' | translate }}</h3>
              <div class="table-wrap" [appScrollRegion]="'dashboard.liquidityTitle' | translate">
                <!-- Two columns, the origin under the position: the table has to fit a half-width card. -->
                <table class="table">
                  <thead>
                    <tr>
                      <th scope="col">
                        {{ 'dashboard.position' | translate }}
                        <span class="sr-only">, {{ 'dashboard.origin' | translate }}</span>
                      </th>
                      <th scope="col" class="num">CHF</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td>
                        {{ 'figure.fin.cash' | translate }}
                        <span class="meta block">{{
                          figure('fin.cash')?.source ?? ('common.dash' | translate)
                        }}</span>
                      </td>
                      <td class="num strong">{{ figure('fin.cash')?.value | chf }}</td>
                    </tr>
                    <tr>
                      <td>
                        {{ 'dashboard.minimumLiquidity' | translate }}
                        <span class="meta block">{{
                          'dashboard.minimumLiquiditySource' | translate
                        }}</span>
                      </td>
                      <td class="num strong">{{ c.minimumLiquidity | chf }}</td>
                    </tr>
                    <tr>
                      <td>
                        {{ 'dashboard.investable' | translate }}
                        <span class="meta block">{{ 'dashboard.investableRule' | translate }}</span>
                      </td>
                      <td class="num strong">{{ a.liquidity.excess | chf }}</td>
                    </tr>
                    <tr>
                      <td>
                        {{ 'figure.fin.floatingRateDebt' | translate }}
                        <span class="meta block">{{
                          figure('fin.floatingRateDebt')?.source ?? ('common.dash' | translate)
                        }}</span>
                      </td>
                      <td class="num strong">{{ figure('fin.floatingRateDebt')?.value | chf }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>
          }

          <section class="card" aria-labelledby="doc-title">
            <h3 id="doc-title">{{ 'start.documents' | translate }}</h3>
            <ul class="doc-status-list">
              @for (d of c.documents; track d.id) {
                <li>
                  <span class="file-name">{{ d.fileName }}</span>
                  <app-badge [tone]="documentTone[d.status]">{{
                    'documentStatus.' + d.status | translate
                  }}</app-badge>
                </li>
              } @empty {
                <li class="meta-lg">{{ 'start.noDocumentsYet' | translate }}</li>
              }
            </ul>
            <a class="link-action" routerLink="/" [queryParams]="{ client: c.id }">{{
              'dashboard.manageDocuments' | translate
            }}</a>
          </section>
        </div>

        <div class="column">
          <h2>{{ 'dashboard.profileTitle' | translate }}</h2>
          @if (assessment(); as a) {
            <section class="panel" aria-labelledby="status-title">
              <h3 id="status-title">{{ 'dashboard.statusTitle' | translate }}</h3>
              <dl class="facts facts-stacked">
                <div>
                  <dt>{{ 'dashboard.status' | translate }}</dt>
                  <dd>
                    @if (a.approvalStatus === 'APPROVED') {
                      <app-badge tone="ok">{{ 'clientStatus.APPROVED' | translate }}</app-badge>
                    } @else {
                      <app-badge tone="pending">{{
                        'clientStatus.IN_PROGRESS' | translate
                      }}</app-badge>
                    }
                  </dd>
                </div>
                <div>
                  <dt>{{ 'dashboard.openProposals' | translate }}</dt>
                  <dd class="inline-actions">
                    @if (a.unconfirmedAnswers) {
                      <b>{{
                        plural('dashboard.toConfirm', a.unconfirmedAnswers)
                          | translate: { count: a.unconfirmedAnswers }
                      }}</b>
                      <a [routerLink]="['/clients', c.id, 'profilierung']">{{
                        'common.confirm' | translate
                      }}</a>
                    } @else {
                      <b>{{ 'dashboard.noneOpen' | translate }}</b>
                    }
                  </dd>
                </div>
                <div>
                  <dt>{{ 'dashboard.checklist' | translate }}</dt>
                  <dd class="strong">
                    {{ 'common.xOfY' | translate: { x: checklistDone(a), y: a.checklist.length } }}
                  </dd>
                </div>
                <div>
                  <dt>{{ 'common.approval' | translate }}</dt>
                  <dd class="strong">{{ 'approval.' + a.approvalStatus | translate }}</dd>
                </div>
              </dl>
            </section>

            <section class="card" aria-labelledby="seg-title">
              <div class="card-head-row">
                <h3 id="seg-title">{{ 'common.clientSegment' | translate }}</h3>
                <span class="tag-rule">{{ 'common.ruleEngine' | translate }}</span>
              </div>
              <p class="rule-result">
                {{
                  a.segment.code
                    ? ('segment.' + a.segment.code | translate)
                    : ('common.dash' | translate)
                }}
              </p>
              <p class="body-sm">{{ a.segment.reason }}</p>
              <p class="meta">
                {{ 'common.legalBasis' | translate: { basis: a.segment.legalBasis } }} ·
                {{ 'dashboard.thresholds' | translate: { count: a.segment.thresholdsExceeded } }}
              </p>
            </section>

            <section class="card" aria-labelledby="rp-title">
              <div class="card-head-row">
                <h3 id="rp-title">{{ 'dashboard.riskProfile' | translate }}</h3>
                <span class="tag-rule">{{ 'common.ruleEngine' | translate }}</span>
              </div>
              <p class="rule-result">
                {{ a.profile.score }} ·
                {{
                  a.profile.code
                    ? ('riskProfile.' + a.profile.code | translate)
                    : ('common.dash' | translate)
                }}
              </p>
              <p class="body-sm">{{ 'dashboard.profileRule' | translate }}</p>
              <div class="score-block">
                <div class="score-head">
                  <span class="strong">{{ 'profile.capacity' | translate }}</span>
                  <b class="score-value">{{ a.capacity.score }}/5</b>
                </div>
                <span class="meta">{{ 'profile.objective' | translate }}</span>
                <app-factor-list [factors]="a.capacity.factors" />
              </div>
              <div class="score-block">
                <div class="score-head">
                  <span class="strong">{{ 'profile.willingness' | translate }}</span>
                  <b class="score-value">{{ a.willingness.score }}/5</b>
                </div>
                <span class="meta">{{ 'profile.subjective' | translate }}</span>
                <app-factor-list [factors]="a.willingness.factors" />
              </div>
            </section>

            <section class="card" aria-labelledby="kn-title">
              <div class="card-head-row">
                <h3 id="kn-title">{{ 'dashboard.knowledgeTitle' | translate }}</h3>
                <span class="meta-lg">{{ 'dashboard.knowledgeBasis' | translate }}</span>
              </div>
              <div class="table-wrap" [appScrollRegion]="'dashboard.knowledgeTitle' | translate">
                <!-- Two columns, the knowledge under the product: the table has to fit a half-width card. -->
                <table class="table">
                  <thead>
                    <tr>
                      <th scope="col">
                        {{ 'questionnaire.productClass' | translate }}
                        <span class="sr-only">, {{ 'dashboard.knowledge' | translate }}</span>
                      </th>
                      <th scope="col">{{ 'common.suitability' | translate }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (s of a.suitability; track s.product) {
                      <tr>
                        <th scope="row">
                          {{ 'product.' + s.product | translate }}
                          <span class="meta block">
                            {{ 'dashboard.knowledge' | translate }}:
                            {{
                              s.knowledgePresumed
                                ? ('knowledge.presumed' | translate)
                                : s.knowledge
                                  ? ('knowledge.' + s.knowledge | translate)
                                  : ('common.dash' | translate)
                            }}
                          </span>
                          @if (s.kidRequired) {
                            <span class="meta block">{{ 'common.kidRequired' | translate }}</span>
                          }
                        </th>
                        <td>
                          <span class="cell-stack">
                            <app-badge [tone]="suitabilityTone[s.suitability]">{{
                              'suitability.' + s.suitability | translate
                            }}</app-badge>
                            @if (s.note) {
                              <span class="meta">{{ s.note }}</span>
                            }
                          </span>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </section>
          } @else if (!dataMode.backendUnavailable()) {
            <section class="card">
              <p class="meta-lg" [attr.role]="assessmentError() ? null : 'status'">
                {{ assessmentError() ?? ('common.loading' | translate) }}
              </p>
            </section>
          }
        </div>
      </div>
    } @else if (!dataMode.backendUnavailable()) {
      <p [class.error]="error()" [attr.role]="error() ? 'alert' : 'status'">
        {{ error() ?? ('dashboard.loading' | translate) }}
      </p>
    }
  `,
})
export class DashboardPage {
  readonly id = input.required<string>();

  private readonly api = inject(TreasuryApi);
  private readonly refresh = inject(RefreshBus);
  private readonly translate = inject(TranslateService);

  protected readonly dataMode = inject(DataMode);
  protected readonly documentTone = DOCUMENT_TONE;
  protected readonly riskTone = RISK_TONE;
  protected readonly suitabilityTone = SUITABILITY_TONE;
  protected readonly plural = plural;
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

  protected figure(key: string): KeyFigure | null {
    return this.client()?.figures.find((f) => f.key === key) ?? null;
  }

  protected currencyBlocks(): readonly CurrencyBlock[] {
    const exposures = this.assessment()?.exposures ?? [];
    return (this.client()?.fxFlows ?? []).map((flow) => ({
      flow,
      exposure: exposures.find((e) => e.currency === flow.currency) ?? null,
    }));
  }

  protected checklistDone(assessment: Assessment): number {
    return assessment.checklist.filter((i) => i.done).length;
  }

  private async load(id: string): Promise<void> {
    try {
      this.client.set(await this.api.getClient(id));
      this.error.set(null);
    } catch (e) {
      this.error.set(
        inlineError(e, (detail) => this.translate.instant('dashboard.loadFailed', { detail })),
      );
      return;
    }
    try {
      this.assessment.set(await this.api.getAssessment(id));
      this.assessmentError.set(null);
    } catch (e) {
      this.assessment.set(null);
      this.assessmentError.set(inlineError(e));
    }
  }
}
