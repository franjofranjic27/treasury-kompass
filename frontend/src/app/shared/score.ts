import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { translate } from '@ngx-translate/core';
import { ScoredFactor } from '../core/models';

const MAX_SCORE = 5;

/** Score 1 to 5 as filled squares; screen readers get «3 von 5». */
@Component({
  selector: 'app-score-dots',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { role: 'img', '[attr.aria-label]': 'label()' },
  styleUrl: './score-dots.scss',
  template: `
    @for (filled of dots(); track $index) {
      <span class="dot" [class.filled]="filled"></span>
    }
  `,
})
export class ScoreDots {
  readonly score = input.required<number>();

  protected readonly label = translate('common.scoreOf', () => ({
    score: this.score(),
    max: MAX_SCORE,
  }));
  protected readonly dots = computed(() =>
    Array.from({ length: MAX_SCORE }, (_, i) => i < this.score()),
  );
}

/** Factors of risk capacity or risk willingness with their score, as on dashboard and questionnaire. */
@Component({
  selector: 'app-factor-list',
  imports: [ScoreDots],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './factor-list.scss',
  template: `
    <ul class="factor-list">
      @for (f of factors(); track f.label) {
        <li>
          <span class="factor-text">
            <span>{{ f.label }}</span>
            <span class="meta">{{ f.value }}</span>
          </span>
          <app-score-dots [score]="f.score" />
        </li>
      }
    </ul>
  `,
})
export class FactorList {
  readonly factors = input.required<readonly ScoredFactor[]>();
}
