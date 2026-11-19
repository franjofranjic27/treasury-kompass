import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { KeyFigure } from '../core/models';

/**
 * Fundstelle of a figure. Values that still need the advisor's confirmation carry the «Vorschlag»
 * tag, as in the questionnaire; confirmed values show their source only.
 */
@Component({
  selector: 'app-figure-source',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    :host {
      display: inline-flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 2px 6px;
    }
  `,
  template: `
    @let f = figure();
    @if (f.status === 'PROPOSED') {
      <span class="tag-proposal">{{ 'common.proposal' | translate }}</span>
    }
    @if (f.source) {
      <span>{{ f.source }}</span>
    } @else {
      <span>{{
        (f.origin === 'ADVISOR' ? 'questionnaire.byAdvisor' : 'common.dash') | translate
      }}</span>
    }
  `,
})
export class FigureSource {
  readonly figure = input.required<Pick<KeyFigure, 'status' | 'origin' | 'source'>>();
}
