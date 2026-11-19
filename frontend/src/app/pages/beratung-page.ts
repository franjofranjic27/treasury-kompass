import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CurrentClient } from '../core/current-client';
import { DataMode } from '../core/data-mode';
import { ChfPipe, MioPipe, PctPipe } from '../core/format';
import { Assessment, ChecklistItem, Exposure } from '../core/models';
import { RefreshBus } from '../core/refresh-bus';
import { TreasuryApi } from '../core/treasury-api';
import { Badge } from '../shared/badge';
import { CaseNav } from '../shared/case-nav';
import { Icon } from '../shared/icon';
import { jumpTo } from '../shared/jump-to';
import { ScrollRegion } from '../shared/scroll-region';
import { HedgeCard, HedgeSettingsChange } from './advisory/hedge-card';
import { SUITABILITY_TONE, inlineError, plural } from './labels';

interface Blocker {
  readonly key: string;
  readonly count: number;
}

@Component({
  selector: 'app-beratung-page',
  imports: [
    RouterLink,
    TranslatePipe,
    ChfPipe,
    MioPipe,
    PctPipe,
    Badge,
    CaseNav,
    Icon,
    HedgeCard,
    ScrollRegion,
  ],
  styleUrl: './beratung-page.scss',
  template: `
    <app-case-nav [clientId]="id()" />
    @if (assessment(); as a) {
      <div class="page-title">
        <span class="eyebrow">
          {{ currentClient.name() ?? '' }}
          @if (a.segment.code) {
            · {{ 'segment.' + a.segment.code | translate }}
          }
          @if (a.profile.code) {
            ·
            {{
              'advisory.profileEyebrow'
                | translate
                  : {
                      score: a.profile.score,
                      profile: ('riskProfile.' + a.profile.code | translate),
                    }
            }}
          }
        </span>
        <h1>{{ 'advisory.title' | translate }}</h1>
        <p class="lead">{{ 'advisory.lead' | translate }}</p>
      </div>
    } @else {
      <div class="page-title">
        <h1>{{ 'advisory.title' | translate }}</h1>
      </div>
    }

    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }

    @if (assessment(); as a) {
      @let approved = a.approvalStatus === 'APPROVED';
      <div role="status" aria-live="polite">
        @if (approved) {
          <div class="banner banner-ok">
            <app-icon name="check" />
            <span
              ><b>{{ 'advisory.bannerApproved' | translate }}</b>
              {{ 'advisory.bannerApprovedNote' | translate }}</span
            >
          </div>
        } @else {
          <div class="banner">
            <app-icon name="info" />
            <span>
              <b>{{ 'advisory.bannerDraft' | translate }}</b>
              {{ blockers().length ? blockerText() : ('advisory.readyToApprove' | translate) }}
            </span>
            <a href="#freigabe" (click)="jumpTo($event, 'freigabe')">{{
              'advisory.toApproval' | translate
            }}</a>
          </div>
        }
      </div>

      <section class="kpis" [attr.aria-label]="'advisory.overview' | translate">
        <div class="kpi">
          <span class="kpi-label">{{ 'advisory.kpiHedged' | translate }}</span>
          <span class="kpi-value">CHF {{ totals().hedgedChf | mio: 2 }}</span>
          <span class="meta">{{
            'advisory.kpiHedgedNote' | translate: { currencies: currencies() }
          }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">{{ 'advisory.kpiCarry' | translate }}</span>
          <span class="kpi-value">CHF {{ totals().carryChf | mio: 2 : true }}</span>
          <span class="meta">{{ 'advisory.kpiCarryNote' | translate }}</span>
        </div>
        <div class="kpi">
          <span class="kpi-label">{{ 'advisory.kpiInvestable' | translate }}</span>
          <span class="kpi-value">CHF {{ a.liquidity.excess | mio: 1 }}</span>
          <span class="meta">{{ 'advisory.kpiInvestableNote' | translate }}</span>
        </div>
      </section>

      <h2>{{ 'advisory.hedgingTitle' | translate }}</h2>
      @for (h of a.hedges; track h.currency) {
        <app-hedge-card
          [hedge]="h"
          [exposure]="exposureOf(a, h.currency)"
          [collapsible]="$index > 0"
          (settingsChange)="changeSettings($event)"
        />
      } @empty {
        <p class="meta-lg">{{ 'advisory.noHedges' | translate }}</p>
      }

      <h2>{{ 'advisory.liquidityTitle' | translate }}</h2>
      <section class="card card-roomy" aria-labelledby="alloc-title">
        <div class="card-head-row">
          <h3 id="alloc-title">{{ 'advisory.allocationTitle' | translate }}</h3>
          @if (a.profile.code) {
            <span class="meta-lg">{{
              'advisory.profileEyebrow'
                | translate
                  : {
                      score: a.profile.score,
                      profile: ('riskProfile.' + a.profile.code | translate),
                    }
            }}</span>
          }
        </div>
        <dl class="facts">
          <div>
            <dt>{{ 'advisory.investment' | translate }}</dt>
            <dd class="strong">{{ a.liquidity.excess | chf }}</dd>
            <dd class="meta">{{ 'dashboard.investableRule' | translate }}</dd>
          </div>
        </dl>
        @if (allocation().length) {
          <div class="allocation">
            <div class="allocation-bar" role="img" [attr.aria-label]="allocationLabel()">
              @for (p of allocation(); track p.product) {
                <span
                  class="allocation-part"
                  [attr.data-index]="$index"
                  [style.flex-grow]="p.share"
                ></span>
              }
            </div>
            <ul class="legend" aria-hidden="true">
              @for (p of allocation(); track p.product) {
                <li>
                  <span class="swatch" [attr.data-index]="$index"></span>
                  {{ 'product.' + p.product | translate }}
                  <b>{{ p.share | pct: false }}</b>
                </li>
              }
            </ul>
          </div>
        }
        <div class="table-wrap" [appScrollRegion]="'advisory.allocationCaption' | translate">
          <table class="table">
            <caption class="sr-only">
              {{
                'advisory.allocationCaption' | translate
              }}
            </caption>
            <thead>
              <tr>
                <th scope="col">{{ 'advisory.product' | translate }}</th>
                <th scope="col" class="num">{{ 'advisory.share' | translate }}</th>
                <th scope="col" class="num">CHF</th>
                <th scope="col">{{ 'common.suitability' | translate }}</th>
              </tr>
            </thead>
            <tbody>
              @for (p of a.liquidity.parts; track p.product) {
                <tr>
                  <td>{{ 'product.' + p.product | translate }}</td>
                  <td class="num">{{ p.share | pct: false }}</td>
                  <td class="num">{{ p.amount | chf }}</td>
                  <td>
                    <app-badge [tone]="suitabilityTone[p.suitability]">{{
                      'suitability.' + p.suitability | translate
                    }}</app-badge>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </section>

      <section class="card card-roomy" aria-labelledby="elig-title">
        <div class="card-head-row">
          <h3 id="elig-title">{{ 'advisory.suitabilityTitle' | translate }}</h3>
          <span class="tag-rule">{{ 'common.ruleEngine' | translate }}</span>
        </div>
        <p class="meta-lg">{{ 'advisory.suitabilityLead' | translate }}</p>
        <div class="table-wrap" [appScrollRegion]="'advisory.suitabilityTitle' | translate">
          <table class="table">
            <thead>
              <tr>
                <th scope="col">{{ 'advisory.product' | translate }}</th>
                <th scope="col">{{ 'advisory.result' | translate }}</th>
                <th scope="col">{{ 'advisory.basis' | translate }}</th>
              </tr>
            </thead>
            <tbody>
              @for (s of a.suitability; track s.product) {
                <tr>
                  <th scope="row">
                    {{ 'product.' + s.product | translate }}
                    @if (s.kidRequired) {
                      <span class="meta block">{{ 'common.kidRequired' | translate }}</span>
                    }
                  </th>
                  <td>
                    <app-badge [tone]="suitabilityTone[s.suitability]">{{
                      'suitability.' + s.suitability | translate
                    }}</app-badge>
                  </td>
                  <td class="src">{{ s.note ?? ('common.dash' | translate) }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </section>

      <h2 id="freigabe">{{ 'advisory.closingTitle' | translate }}</h2>
      <section class="card card-roomy" aria-labelledby="check-title">
        <h3 id="check-title">{{ 'advisory.beforeClosing' | translate }}</h3>

        <div class="sub-block">
          <h4>{{ 'advisory.prefilledTitle' | translate }}</h4>
          @if (a.unconfirmedAnswers) {
            <div class="prefill-row">
              <span>{{
                plural('advisory.prefilledOpen', a.unconfirmedAnswers)
                  | translate: { count: a.unconfirmedAnswers }
              }}</span>
              <a class="btn btn-secondary" [routerLink]="['/clients', id(), 'profilierung']">{{
                'advisory.confirmInQuestionnaire' | translate
              }}</a>
            </div>
          } @else {
            <app-badge tone="ok">{{ 'advisory.allConfirmed' | translate }}</app-badge>
          }
        </div>

        <fieldset class="checklist">
          <legend>
            {{
              'advisory.checklistLegend'
                | translate: { done: checklistDone(a), total: a.checklist.length }
            }}
          </legend>
          @for (item of a.checklist; track item.id) {
            <div class="check">
              <input
                type="checkbox"
                [id]="'chk-' + item.id"
                [checked]="item.done"
                (change)="toggle(item, $event)"
              />
              <label [for]="'chk-' + item.id">
                <span>{{ item.text }}</span>
                <span class="meta">{{ item.legalBasis }}</span>
              </label>
            </div>
          }
        </fieldset>

        <div class="approval-block">
          @if (approved) {
            <app-badge tone="ok">{{ 'advisory.approvedBadge' | translate }}</app-badge>
          } @else {
            <div class="approve-row">
              <button
                class="btn btn-primary"
                type="button"
                aria-describedby="approve-reason"
                [disabled]="blockers().length > 0 || approving()"
                (click)="approve(a)"
              >
                {{ 'advisory.approve' | translate }}
              </button>
              <p id="approve-reason" class="meta-lg">
                {{ blockers().length ? blockerText() : ('advisory.readyToApprove' | translate) }}
              </p>
            </div>
          }
          <p class="meta">{{ 'advisory.approvalNote' | translate }}</p>
        </div>
      </section>
    } @else if (!error() && !dataMode.backendUnavailable()) {
      <p class="meta-lg" role="status">{{ 'advisory.loading' | translate }}</p>
    }
  `,
})
export class BeratungPage {
  readonly id = input.required<string>();

  protected readonly currentClient = inject(CurrentClient);
  protected readonly dataMode = inject(DataMode);
  private readonly api = inject(TreasuryApi);
  private readonly refresh = inject(RefreshBus);
  private readonly translate = inject(TranslateService);

  protected readonly suitabilityTone = SUITABILITY_TONE;
  protected readonly plural = plural;
  protected readonly jumpTo = jumpTo;
  protected readonly assessment = signal<Assessment | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly approving = signal(false);

  protected readonly blockers = computed<readonly Blocker[]>(() => {
    const a = this.assessment();
    if (!a) return [];
    const open = a.checklist.filter((i) => !i.done).length;
    return [
      ...(a.unconfirmedAnswers
        ? [
            {
              key: plural('advisory.blockerAnswers', a.unconfirmedAnswers),
              count: a.unconfirmedAnswers,
            },
          ]
        : []),
      ...(open ? [{ key: plural('advisory.blockerChecklist', open), count: open }] : []),
    ];
  });

  protected readonly totals = computed(() => {
    const hedges = this.assessment()?.hedges ?? [];
    return {
      hedgedChf: hedges.reduce((sum, h) => sum + h.hedgedAmountChf, 0),
      carryChf: hedges.reduce((sum, h) => sum + h.carryChf, 0),
    };
  });

  protected readonly currencies = computed(() =>
    (this.assessment()?.hedges ?? []).map((h) => h.currency).join(', '),
  );

  protected readonly allocation = computed(() =>
    (this.assessment()?.liquidity.parts ?? []).filter((p) => p.share > 0),
  );

  constructor() {
    effect(() => {
      this.refresh.tick();
      void this.load(this.id());
    });
  }

  /** Blockers as one sentence; re-evaluated on every change detection, so it follows the language. */
  protected blockerText(): string {
    return this.blockers()
      .map((b) => this.translate.instant(b.key, { count: b.count }))
      .join(' · ');
  }

  protected allocationLabel(): string {
    const parts = this.allocation()
      .map((p) =>
        this.translate.instant('advisory.allocationPart', {
          product: this.translate.instant('product.' + p.product),
          share: p.share,
        }),
      )
      .join(', ');
    return this.translate.instant('advisory.allocationAria', { parts });
  }

  protected exposureOf(assessment: Assessment, currency: string): Exposure | null {
    return assessment.exposures.find((e) => e.currency === currency) ?? null;
  }

  protected checklistDone(assessment: Assessment): number {
    return assessment.checklist.filter((i) => i.done).length;
  }

  protected async toggle(item: ChecklistItem, event: Event): Promise<void> {
    const done = (event.target as HTMLInputElement).checked;
    await this.run(() => this.api.setChecklistItem(this.id(), item.id, done));
  }

  protected async changeSettings(change: HedgeSettingsChange): Promise<void> {
    await this.run(() => this.api.setHedgeSettings(this.id(), change.currency, change.settings));
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
      this.error.set(inlineError(e));
    }
  }

  private async load(id: string): Promise<void> {
    try {
      this.assessment.set(await this.api.getAssessment(id));
      this.error.set(null);
    } catch (e) {
      this.error.set(
        inlineError(e, (detail) => this.translate.instant('advisory.loadFailed', { detail })),
      );
    }
  }
}
