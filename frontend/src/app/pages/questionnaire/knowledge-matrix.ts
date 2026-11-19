import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { ChoiceOption, Question } from '../../core/models';
import { ScrollRegion } from '../../shared/scroll-region';
import { AnswerState } from './answer-state';

export interface MatrixAnswer {
  readonly question: Question;
  readonly value: string;
}

/** Knowledge per product class as one radio matrix (rows: products, columns: knowledge levels). */
@Component({
  selector: 'app-knowledge-matrix',
  imports: [TranslatePipe, AnswerState, ScrollRegion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './knowledge-matrix.scss',
  template: `
    <div class="table-wrap" [appScrollRegion]="'questionnaire.knowledgeCaption' | translate">
      <table class="table matrix">
        <caption class="sr-only">
          {{
            'questionnaire.knowledgeCaption' | translate
          }}
        </caption>
        <thead>
          <tr>
            <th scope="col">{{ 'questionnaire.productClass' | translate }}</th>
            @for (level of levels(); track level.value) {
              <th scope="col" class="center">{{ level.label }}</th>
            }
            <th scope="col">{{ 'questionnaire.answerState' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          @for (q of questions(); track q.key) {
            <tr [class.row-proposed]="q.status === 'PROPOSED'">
              <th scope="row">{{ q.text }}</th>
              @for (level of levels(); track level.value) {
                <td class="center">
                  <label class="radio-cell">
                    <input
                      type="radio"
                      [name]="'q-' + q.key"
                      [value]="level.value"
                      [checked]="q.value === level.value"
                      (change)="save.emit({ question: q, value: level.value })"
                    />
                    <span class="sr-only">{{ q.text }}: {{ level.label }}</span>
                  </label>
                </td>
              }
              <td>
                <app-answer-state [question]="q" [compact]="true" (confirm)="confirm.emit(q)" />
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class KnowledgeMatrix {
  readonly questions = input.required<readonly Question[]>();
  readonly save = output<MatrixAnswer>();
  readonly confirm = output<Question>();

  protected readonly levels = computed<readonly ChoiceOption[]>(
    () => this.questions()[0]?.options ?? [],
  );
}

/** A section renders as matrix when all its questions share the knowledge-level scale. */
export function isKnowledgeMatrix(questions: readonly Question[]): boolean {
  return questions.length > 1 && questions.every((q) => q.type === 'KNOWLEDGE_LEVEL');
}
