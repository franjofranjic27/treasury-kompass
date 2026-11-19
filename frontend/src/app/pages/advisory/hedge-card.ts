import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { MioPipe, PctPipe } from '../../core/format';
import { Exposure, HedgeProposal, HedgeSettings } from '../../core/models';
import { Icon } from '../../shared/icon';
import { ScrollRegion } from '../../shared/scroll-region';

export interface HedgeSettingsChange {
  readonly currency: string;
  readonly settings: HedgeSettings;
}

const STEP_DOWN_OPTIONS: readonly number[] = [0, 5, 10, 15];
const MONTHS_PER_TRANCHE = 3;

/**
 * Hedge proposal of one currency. Ratios come from the rule engine; the advisor only sets the
 * start ratio (capped by the treasury policy) and the step-down, which the backend recalculates.
 */
@Component({
  selector: 'app-hedge-card',
  imports: [TranslatePipe, MioPipe, PctPipe, Icon, ScrollRegion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './hedge-card.scss',
  template: `
    @let h = hedge();
    @let e = exposure();
    <section class="card card-roomy hedge-card" [attr.aria-labelledby]="ids().title">
      @if (collapsible()) {
        <h3 class="collapse-heading" [id]="ids().title">
          <button
            type="button"
            class="collapse-toggle"
            [attr.aria-expanded]="expanded()"
            [attr.aria-controls]="ids().body"
            (click)="expanded.set(!expanded())"
          >
            <span class="collapse-text">
              <span class="collapse-title">{{ h.action }}</span>
              <span class="meta-lg">{{
                'advisory.collapsedSummary'
                  | translate
                    : {
                        ratios: h.ratios.join(' / '),
                        average: (h.averageRatio | pct: false),
                        carry: (h.carryChf | mio: 2 : true),
                      }
              }}</span>
            </span>
            <span class="collapse-icon" [class.open]="expanded()" aria-hidden="true">
              <app-icon name="chevron-down" />
            </span>
          </button>
        </h3>
      } @else {
        <div class="hedge-head">
          <div class="card-title">
            <h3 [id]="ids().title">{{ h.action }}</h3>
            @if (e) {
              <span class="meta-lg">{{
                'advisory.netFlowLine'
                  | translate
                    : {
                        direction: ('direction.' + e.direction | translate),
                        amount: (e.netPosition | mio: 1 : true),
                        currency: h.currency,
                      }
              }}</span>
            }
          </div>
          <div class="kpi-inline">
            <span class="meta">{{ 'advisory.averageRatio' | translate }}</span>
            <span class="kpi-value">{{ h.averageRatio | pct: false }}</span>
          </div>
        </div>
      }

      @if (!collapsible() || expanded()) {
        <div class="hedge-body" [id]="ids().body">
          <dl class="facts">
            @if (e) {
              <div>
                <dt>{{ 'advisory.netPosition' | translate }}</dt>
                <dd class="strong">{{ h.currency }} {{ e.netPosition | mio: 1 : true }}</dd>
                <dd class="meta">CHF {{ e.netPositionChf | mio: 2 : true }}</dd>
              </div>
            }
            <div>
              <dt>{{ 'advisory.cap' | translate }}</dt>
              <dd class="strong">{{ h.cap | pct: false }}</dd>
              <dd class="meta">{{ 'advisory.capRule' | translate }}</dd>
            </div>
            <div>
              <dt>{{ 'advisory.hedged' | translate }}</dt>
              <dd class="strong">{{ h.currency }} {{ h.hedgedAmountFx | mio: 2 }}</dd>
              <dd class="meta">CHF {{ h.hedgedAmountChf | mio: 2 }}</dd>
            </div>
            <div>
              <dt>{{ 'advisory.carry' | translate }}</dt>
              <dd class="strong">CHF {{ h.carryChf | mio: 2 : true }}</dd>
              <dd class="meta">{{ 'advisory.carryNote' | translate }}</dd>
            </div>
            @if (h.collarShare !== null) {
              <div>
                <dt>{{ 'advisory.collarShare' | translate }}</dt>
                <dd class="strong">{{ h.collarShare | pct }}</dd>
              </div>
            }
          </dl>

          <div class="hedge-controls">
            <div class="control">
              <div class="control-head">
                <label [for]="ids().start">{{ 'advisory.startRatio' | translate }}</label>
                <output [for]="ids().start">{{ startValue() | pct: false }}</output>
              </div>
              <input
                type="range"
                min="0"
                step="5"
                [id]="ids().start"
                [max]="h.cap"
                [value]="startValue()"
                [attr.aria-describedby]="ids().cap"
                (input)="draftStart.set(numberOf($event))"
                (change)="emitStart($event)"
              />
              <div class="range-scale" aria-hidden="true">
                <span>{{ 0 | pct: false }}</span>
                <span>{{ 'advisory.capScale' | translate: { cap: (h.cap | pct: false) } }}</span>
              </div>
              <p class="meta" [id]="ids().cap">
                {{ 'advisory.capHint' | translate: { cap: (h.cap | pct: false) } }}
              </p>
              @if (h.limitedByPolicy) {
                <p class="meta strong">{{ 'advisory.limitedByPolicy' | translate }}</p>
              }
            </div>
            <div class="control">
              <div class="field field-select">
                <label [for]="ids().step">{{ 'advisory.stepDown' | translate }}</label>
                <div class="select-wrap">
                  <select [id]="ids().step" (change)="emitStepDown($event)">
                    @for (option of stepDownOptions; track option) {
                      <option [value]="option" [selected]="option === h.stepDown">
                        {{
                          option === 0
                            ? ('advisory.noStepDown' | translate)
                            : ('advisory.stepDownPoints' | translate: { points: option })
                        }}
                      </option>
                    }
                  </select>
                  <app-icon name="chevron-down" />
                </div>
              </div>
              <p class="meta">{{ 'advisory.stepDownHint' | translate }}</p>
            </div>
          </div>

          <div
            class="table-wrap"
            [appScrollRegion]="'advisory.trancheCaption' | translate: { currency: h.currency }"
          >
            <table class="table">
              <caption class="sr-only">
                {{
                  'advisory.trancheCaption' | translate: { currency: h.currency }
                }}
              </caption>
              <thead>
                <tr>
                  <th scope="col">{{ 'advisory.maturity' | translate }}</th>
                  <th scope="col" class="num">{{ 'advisory.ratio' | translate }}</th>
                </tr>
              </thead>
              <tbody>
                @for (ratio of h.ratios; track $index) {
                  <tr>
                    <td>
                      {{
                        'advisory.months' | translate: { months: ($index + 1) * monthsPerTranche }
                      }}
                    </td>
                    <td class="num">
                      <span class="tranche">
                        <span class="tranche-bar" aria-hidden="true">
                          <span class="tranche-fill" [style.width.%]="ratio"></span>
                          <span class="tranche-cap" [style.left.%]="h.cap"></span>
                        </span>
                        <span class="tranche-value">{{ ratio | pct: false }}</span>
                      </span>
                    </td>
                  </tr>
                }
              </tbody>
              <tfoot>
                <tr>
                  <td>{{ 'advisory.total' | translate }}</td>
                  <td class="num">Ø {{ h.averageRatio | pct: false }}</td>
                </tr>
              </tfoot>
            </table>
          </div>

          @if (h.scenarioUnhedgedChf !== null && h.scenarioHedgedChf !== null) {
            <div class="scenario">
              <h4>{{ 'advisory.scenarioTitle' | translate: { currency: h.currency } }}</h4>
              <ul class="scenario-list">
                <li>
                  <span class="scenario-row">
                    <span>{{ 'advisory.unhedged' | translate }}</span>
                    <b>CHF {{ h.scenarioUnhedgedChf | mio: 2 }}</b>
                  </span>
                  <span class="scenario-bar unhedged" aria-hidden="true"></span>
                </li>
                <li>
                  <span class="scenario-row">
                    <span>{{ 'advisory.withProposal' | translate }}</span>
                    <b>CHF {{ h.scenarioHedgedChf | mio: 2 }}</b>
                  </span>
                  <span
                    class="scenario-bar hedged"
                    aria-hidden="true"
                    [style.width.%]="scenarioWidth()"
                  ></span>
                </li>
              </ul>
              <p class="meta">{{ 'advisory.scenarioNote' | translate }}</p>
            </div>
          }
        </div>
      }
    </section>
  `,
})
export class HedgeCard {
  readonly hedge = input.required<HedgeProposal>();
  readonly exposure = input<Exposure | null>(null);
  readonly collapsible = input(false);
  readonly settingsChange = output<HedgeSettingsChange>();

  protected readonly stepDownOptions = STEP_DOWN_OPTIONS;
  protected readonly monthsPerTranche = MONTHS_PER_TRANCHE;
  protected readonly expanded = signal(false);
  /** Live slider value while dragging; reset as soon as the recalculated proposal arrives. */
  protected readonly draftStart = linkedSignal<HedgeProposal, number | null>({
    source: this.hedge,
    computation: () => null,
  });
  protected readonly startValue = computed(() => this.draftStart() ?? this.hedge().startRatio);

  protected readonly ids = computed(() => {
    const prefix = `hedge-${this.hedge().currency}`;
    return {
      title: `${prefix}-title`,
      body: `${prefix}-body`,
      start: `${prefix}-start`,
      cap: `${prefix}-cap`,
      step: `${prefix}-step`,
    };
  });

  protected readonly scenarioWidth = computed(() => {
    const { scenarioUnhedgedChf: unhedged, scenarioHedgedChf: hedged } = this.hedge();
    if (!unhedged || hedged === null) return 0;
    return Math.min(100, Math.abs(hedged / unhedged) * 100);
  });

  protected numberOf(event: Event): number {
    return Number((event.target as HTMLInputElement | HTMLSelectElement).value);
  }

  protected emitStart(event: Event): void {
    const startRatio = this.numberOf(event);
    if (startRatio === this.hedge().startRatio) return;
    this.emit({ startRatio, stepDown: this.hedge().stepDown });
  }

  protected emitStepDown(event: Event): void {
    this.emit({ startRatio: this.hedge().startRatio, stepDown: this.numberOf(event) });
  }

  private emit(settings: HedgeSettings): void {
    this.settingsChange.emit({ currency: this.hedge().currency, settings });
  }
}
