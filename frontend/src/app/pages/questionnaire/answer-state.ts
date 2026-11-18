import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { Question } from '../../core/models';
import { Badge } from '../../shared/badge';

/** Shows where an answer comes from and lets the advisor confirm a prefilled one. */
@Component({
  selector: 'app-answer-state',
  imports: [TranslatePipe, Badge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.compact]': 'compact()' },
  template: `
    @let q = question();
    @switch (q.status) {
      @case ('PROPOSED') {
        <div class="proposal-box">
          <span class="tag-proposal">{{ 'common.proposal' | translate }}</span>
          <span class="proposal-source">{{
            q.source
              ? ('questionnaire.fromSource' | translate: { source: q.source })
              : ('questionnaire.proposedNoSource' | translate)
          }}</span>
          <button
            class="btn btn-secondary"
            type="button"
            [attr.aria-label]="'questionnaire.confirmAria' | translate: { question: q.text }"
            (click)="confirm.emit()"
          >
            {{ 'common.confirm' | translate }}
          </button>
        </div>
      }
      @case ('CONFIRMED') {
        <div class="answer-meta">
          <app-badge tone="ok">{{ 'common.confirmed' | translate }}</app-badge>
          <span class="meta">{{ originText(q) | translate: { source: q.source } }}</span>
        </div>
      }
      @default {
        <div class="answer-meta">
          <app-badge tone="warn">{{ 'questionnaire.missing' | translate }}</app-badge>
          <span class="meta">{{ 'questionnaire.missingNote' | translate }}</span>
        </div>
      }
    }
  `,
})
export class AnswerState {
  readonly question = input.required<Question>();
  readonly compact = input(false);
  readonly confirm = output();

  protected originText(question: Question): string {
    if (question.origin === 'ADVISOR') {
      return question.source ? 'questionnaire.changedByAdvisor' : 'questionnaire.byAdvisor';
    }
    return question.source ? 'questionnaire.confirmedSource' : 'questionnaire.confirmedNoSource';
  }
}
