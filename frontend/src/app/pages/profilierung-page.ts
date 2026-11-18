import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Question, Questionnaire } from '../core/models';
import { RefreshBus } from '../core/refresh-bus';
import { TreasuryApi } from '../core/treasury-api';
import { errorText } from './labels';

@Component({
  selector: 'app-profilierung-page',
  imports: [RouterLink],
  template: `
    <div class="row between">
      <h1>FIDLEG-Profilierung</h1>
      <span class="pill" [class.ok]="progress().confirmed === progress().total">{{ progress().confirmed }} von {{ progress().total }} bestätigt</span>
    </div>
    @if (error()) { <p class="error">{{ error() }}</p> }
    @if (questionnaire(); as q) {
      <ol class="stepper">
        @for (s of q.sections; track s.id) {
          <li>
            <button type="button" [class.active]="$index === step()" (click)="step.set($index)">
              <span class="step-no">{{ $index + 1 }}</span> {{ s.title }}
              <span class="muted small">{{ confirmedIn($index) }}/{{ s.questions.length }}</span>
            </button>
          </li>
        }
      </ol>
      @if (q.sections[step()]; as section) {
        <section class="card">
          <div class="row between"><h2>{{ section.title }}</h2><span class="muted small">{{ section.legalBasis }}</span></div>
          @for (question of section.questions; track question.key) {
            <div class="question">
              <div class="row between">
                <label class="q-text" [attr.for]="question.key">{{ question.text }}</label>
                @switch (question.status) {
                  @case ('CONFIRMED') { <span class="pill ok">Bestätigt</span> }
                  @case ('PROPOSED') { <button class="btn btn-secondary btn-sm" type="button" (click)="confirm(question)">Bestätigen</button> }
                  @default { <span class="pill warn">Fehlt</span> }
                }
              </div>
              @if (question.hint) { <p class="muted small">{{ question.hint }}</p> }
              @if (question.options.length) {
                <select class="input" [id]="question.key" [value]="question.value ?? ''" (change)="save(question, $any($event.target).value)">
                  <option value="" disabled>Bitte wählen</option>
                  @for (o of question.options; track o.value) {
                    <option [value]="o.value" [selected]="o.value === question.value">{{ o.label }}</option>
                  }
                </select>
              } @else {
                <input class="input" type="number" step="0.1" [id]="question.key" [value]="question.value ?? ''" placeholder="Wert erfassen" (change)="save(question, $any($event.target).value)" />
              }
              @if (question.status === 'PROPOSED' && question.source) {
                <p class="source small"><b>Fundstelle:</b> {{ question.source }}</p>
              } @else if (question.source && question.source !== 'Berater') {
                <p class="muted small">Fundstelle: {{ question.source }}</p>
              }
            </div>
          }
          <div class="row between mt">
            <button class="btn btn-secondary" type="button" [disabled]="step() === 0" (click)="step.set(step() - 1)">Zurück</button>
            @if (step() < q.sections.length - 1) {
              <button class="btn btn-primary" type="button" (click)="step.set(step() + 1)">Weiter</button>
            } @else {
              <a class="btn btn-primary" [routerLink]="['/clients', id()]">Zum Dashboard</a>
            }
          </div>
        </section>
      }
    } @else if (!error()) {
      <p class="muted">Fragebogen wird geladen …</p>
    }
  `
})
export class ProfilierungPage {
  readonly id = input.required<string>();

  private readonly api = inject(TreasuryApi);
  private readonly refresh = inject(RefreshBus);

  protected readonly questionnaire = signal<Questionnaire | null>(null);
  protected readonly step = signal(0);
  protected readonly error = signal<string | null>(null);
  protected readonly progress = computed(() => {
    const questions = this.questionnaire()?.sections.flatMap((s) => s.questions) ?? [];
    return { confirmed: questions.filter((q) => q.status === 'CONFIRMED').length, total: questions.length };
  });

  constructor() {
    effect(() => {
      this.refresh.tick();
      void this.load(this.id());
    });
  }

  protected confirmedIn(index: number): number {
    return this.questionnaire()?.sections[index].questions.filter((q) => q.status === 'CONFIRMED').length ?? 0;
  }

  protected async confirm(question: Question): Promise<void> {
    await this.run(() => this.api.confirmAnswer(this.id(), question.key));
  }

  protected async save(question: Question, value: string): Promise<void> {
    if (value === '' || value === question.value) return;
    await this.run(() => this.api.setAnswer(this.id(), question.key, value));
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
      this.questionnaire.set(await this.api.getQuestionnaire(id));
      this.error.set(null);
    } catch (e) {
      this.error.set(`Fragebogen konnte nicht geladen werden: ${errorText(e)}`);
    }
  }
}
