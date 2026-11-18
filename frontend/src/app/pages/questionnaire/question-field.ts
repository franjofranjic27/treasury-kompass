import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { Question } from '../../core/models';
import { AnswerState } from './answer-state';

/** One questionnaire question: radio group for choices, underlined number field for amounts. */
@Component({
  selector: 'app-question-field',
  imports: [AnswerState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let q = question();
    @if (q.options.length) {
      <fieldset class="question" [attr.aria-describedby]="q.hint ? hintId() : null">
        <legend class="q-text">{{ q.text }}</legend>
        @if (q.hint) {
          <p class="q-hint" [id]="hintId()">{{ q.hint }}</p>
        }
        <div class="radio-group">
          @for (o of q.options; track o.value) {
            <label class="radio">
              <input
                type="radio"
                [name]="inputId()"
                [value]="o.value"
                [checked]="o.value === q.value"
                (change)="save.emit(o.value)"
              />
              <span>{{ o.label }}</span>
            </label>
          }
        </div>
        <app-answer-state [question]="q" (confirm)="confirm.emit()" />
      </fieldset>
    } @else {
      <div class="question">
        <label class="q-text" [for]="inputId()">{{ q.text }}</label>
        @if (q.hint) {
          <p class="q-hint" [id]="hintId()">{{ q.hint }}</p>
        }
        <div class="field field-amount">
          <input
            [id]="inputId()"
            type="number"
            inputmode="decimal"
            step="0.1"
            min="0"
            [value]="q.value ?? ''"
            [attr.aria-describedby]="q.hint ? hintId() : null"
            (change)="onAmount($event)"
          />
          @if (q.unit) {
            <span class="unit">{{ q.unit }}</span>
          }
        </div>
        <app-answer-state [question]="q" (confirm)="confirm.emit()" />
      </div>
    }
  `,
})
export class QuestionField {
  readonly question = input.required<Question>();
  readonly save = output<string>();
  readonly confirm = output();

  protected readonly inputId = computed(() => `q-${this.question().key}`);
  protected readonly hintId = computed(() => `${this.inputId()}-hint`);

  protected onAmount(event: Event): void {
    const value = (event.target as HTMLInputElement).value.trim();
    if (value !== '' && value !== this.question().value) this.save.emit(value);
  }
}
