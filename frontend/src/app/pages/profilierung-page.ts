import {
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CurrentClient } from '../core/current-client';
import { DataMode } from '../core/data-mode';
import { MioPipe, PctPipe } from '../core/format';
import { Assessment, Question, Questionnaire, QuestionnaireSection } from '../core/models';
import { RefreshBus } from '../core/refresh-bus';
import { TreasuryApi } from '../core/treasury-api';
import { Badge } from '../shared/badge';
import { CaseNav } from '../shared/case-nav';
import { Icon } from '../shared/icon';
import { FactorList } from '../shared/score';
import { RISK_TONE, inlineError, plural } from './labels';
import { KnowledgeMatrix, isKnowledgeMatrix } from './questionnaire/knowledge-matrix';
import { QuestionField } from './questionnaire/question-field';

interface Progress {
  readonly confirmed: number;
  readonly total: number;
  readonly prefilledConfirmed: number;
  readonly prefilled: number;
}

@Component({
  selector: 'app-profilierung-page',
  imports: [
    RouterLink,
    TranslatePipe,
    MioPipe,
    PctPipe,
    Badge,
    CaseNav,
    Icon,
    FactorList,
    KnowledgeMatrix,
    QuestionField,
  ],
  styleUrl: './profilierung-page.scss',
  template: `
    <app-case-nav [clientId]="id()" />
    <div class="page-title">
      @if (currentClient.name(); as name) {
        <span class="eyebrow">{{ name }}</span>
      }
      <h1>{{ 'questionnaire.title' | translate }}</h1>
      <p class="lead">{{ 'questionnaire.lead' | translate }}</p>
    </div>

    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }

    @if (questionnaire(); as q) {
      @let p = progress();
      <section class="progress-grid" [attr.aria-label]="'questionnaire.progress' | translate">
        <div class="progress">
          <div class="progress-head">
            <span>{{ 'questionnaire.progressDone' | translate }}</span>
            <b>{{ 'common.xOfY' | translate: { x: p.confirmed, y: p.total } }}</b>
          </div>
          <div class="progress-bar" aria-hidden="true">
            <span [style.width.%]="percent(p.confirmed, p.total)"></span>
          </div>
        </div>
        <div class="progress">
          <div class="progress-head">
            <span>{{ 'questionnaire.progressPrefilled' | translate }}</span>
            <b>{{ 'common.xOfY' | translate: { x: p.prefilledConfirmed, y: p.prefilled } }}</b>
          </div>
          <div class="progress-bar" aria-hidden="true">
            <span [style.width.%]="percent(p.prefilledConfirmed, p.prefilled)"></span>
          </div>
        </div>
      </section>

      <nav [attr.aria-label]="'questionnaire.sections' | translate">
        <ol class="stepper">
          @for (s of q.sections; track s.id) {
            @let open = openCount(s);
            <li>
              <button
                type="button"
                class="step"
                [class.current]="$index === step()"
                [attr.aria-current]="$index === step() ? 'step' : null"
                (click)="step.set($index)"
              >
                <span class="step-circle" [class.complete]="open === 0" aria-hidden="true">
                  @if (open === 0) {
                    <app-icon name="check" [size]="16" [strokeWidth]="2.5" />
                  } @else {
                    {{ $index + 1 }}
                  }
                </span>
                <span class="step-text">
                  <span class="step-label">{{ s.title }}</span>
                  <span class="meta">{{ stepStatus(s) | translate: { count: open } }}</span>
                </span>
              </button>
            </li>
          }
        </ol>
      </nav>

      @if (q.sections[step()]; as section) {
        <section class="card card-xl" aria-labelledby="step-title">
          <div class="card-head-split">
            <div class="card-title">
              <span class="eyebrow">{{
                'questionnaire.stepOf' | translate: { step: step() + 1, total: q.sections.length }
              }}</span>
              <h2 id="step-title" tabindex="-1" #stepHeading>{{ section.title }}</h2>
            </div>
            <span class="meta-lg">{{
              'common.legalBasis' | translate: { basis: section.legalBasis }
            }}</span>
          </div>

          @if (isMatrix(section)) {
            <app-knowledge-matrix
              [questions]="section.questions"
              (save)="save($event.question, $event.value)"
              (confirm)="confirm($event)"
            />
          } @else {
            <div class="question-list" [class.question-grid]="isAmountSection(section)">
              @for (question of section.questions; track question.key) {
                <app-question-field
                  [question]="question"
                  (save)="save(question, $event)"
                  (confirm)="confirm(question)"
                />
              }
            </div>
          }

          @if (assessment(); as a) {
            @switch (section.id) {
              @case ('SEGMENT') {
                <div class="rule-panel">
                  <span class="tag-rule">{{ 'common.ruleEngine' | translate }}</span>
                  <p class="rule-result">
                    {{
                      a.segment.code
                        ? ('segment.' + a.segment.code | translate)
                        : ('common.dash' | translate)
                    }}
                  </p>
                  <p>{{ a.segment.reason }} · {{ a.segment.legalBasis }}</p>
                </div>
              }
              @case ('FINANCIALS') {
                <div class="rule-panel">
                  <div class="rule-head">
                    <span class="tag-rule">{{ 'common.ruleEngine' | translate }}</span>
                    <span class="meta-lg">{{ 'profile.objective' | translate }}</span>
                  </div>
                  <p class="rule-result">
                    {{ 'profile.capacityScore' | translate: { score: a.capacity.score } }}
                  </p>
                  <app-factor-list [factors]="a.capacity.factors" />
                </div>
              }
              @case ('FX_FLOWS') {
                <div class="rule-panel">
                  <div class="rule-head">
                    <span class="tag-rule">{{ 'common.ruleEngine' | translate }}</span>
                    <span class="meta-lg">{{ 'questionnaire.exposureNote' | translate }}</span>
                  </div>
                  <ul class="exposure-list">
                    @for (e of a.exposures; track e.currency) {
                      <li>
                        <span>
                          <b>{{ e.currency }}</b> · {{ 'direction.' + e.direction | translate }}
                          {{ e.currency }} {{ e.netPosition | mio: 1 : true }} ·
                          {{
                            'questionnaire.ebitShare'
                              | translate: { pct: (e.sensitivityPctEbit | pct) }
                          }}
                        </span>
                        <app-badge [tone]="riskTone[e.riskLevel]">{{
                          'risk.' + e.riskLevel | translate
                        }}</app-badge>
                      </li>
                    }
                  </ul>
                </div>
              }
              @case ('GOALS') {
                @if (a.hedges[0]; as hedge) {
                  <div class="rule-panel">
                    <span class="tag-rule">{{ 'common.ruleEngine' | translate }}</span>
                    <p class="rule-result">
                      {{ 'questionnaire.capResult' | translate: { cap: (hedge.cap | pct: false) } }}
                    </p>
                    <p>{{ 'questionnaire.capRule' | translate }}</p>
                  </div>
                }
              }
              @case ('RISK_TOLERANCE') {
                <div class="rule-panel">
                  <div class="rule-head">
                    <span class="tag-rule">{{ 'common.ruleEngine' | translate }}</span>
                    <span class="meta-lg">{{ 'profile.subjective' | translate }}</span>
                  </div>
                  <p class="rule-result">
                    {{ 'profile.willingnessScore' | translate: { score: a.willingness.score } }}
                  </p>
                  <app-factor-list [factors]="a.willingness.factors" />
                  <p class="rule-foot">
                    {{
                      'questionnaire.profileLine'
                        | translate
                          : {
                              score: a.profile.score,
                              profile: a.profile.code
                                ? ('riskProfile.' + a.profile.code | translate)
                                : '–',
                              capacity: a.capacity.score,
                              willingness: a.willingness.score,
                            }
                    }}
                  </p>
                </div>
              }
              @case ('KNOWLEDGE') {
                <div class="note" role="note">
                  <app-icon name="info" />
                  <span>{{ 'questionnaire.recalcNote' | translate }}</span>
                </div>
              }
            }
          }

          <div class="step-actions">
            @if (step() > 0) {
              <button class="btn btn-secondary" type="button" (click)="goTo(step() - 1)">
                {{ 'questionnaire.back' | translate: { section: q.sections[step() - 1].title } }}
              </button>
            } @else {
              <span></span>
            }
            @if (step() < q.sections.length - 1) {
              <button class="btn btn-primary" type="button" (click)="goTo(step() + 1)">
                {{ 'questionnaire.next' | translate: { section: q.sections[step() + 1].title }
                }}<app-icon name="chevron-right" />
              </button>
            } @else {
              <a class="btn btn-primary" [routerLink]="['/clients', id()]"
                >{{ 'common.toDashboard' | translate }}<app-icon name="chevron-right"
              /></a>
            }
          </div>
        </section>
      }
    } @else if (!error() && !dataMode.backendUnavailable()) {
      <p class="meta-lg" role="status">{{ 'questionnaire.loading' | translate }}</p>
    }
  `,
})
export class ProfilierungPage {
  readonly id = input.required<string>();

  protected readonly currentClient = inject(CurrentClient);
  protected readonly dataMode = inject(DataMode);
  private readonly api = inject(TreasuryApi);
  private readonly refresh = inject(RefreshBus);
  private readonly translate = inject(TranslateService);
  private readonly injector = inject(Injector);
  private readonly stepHeading = viewChild<ElementRef<HTMLElement>>('stepHeading');

  protected readonly riskTone = RISK_TONE;
  protected readonly isMatrix = (section: QuestionnaireSection) =>
    isKnowledgeMatrix(section.questions);
  protected readonly questionnaire = signal<Questionnaire | null>(null);
  protected readonly assessment = signal<Assessment | null>(null);
  protected readonly step = signal(0);
  protected readonly error = signal<string | null>(null);
  protected readonly progress = computed<Progress>(() => {
    const questions = this.questionnaire()?.sections.flatMap((s) => s.questions) ?? [];
    const prefilled = questions.filter((q) => q.origin !== null && q.origin !== 'ADVISOR');
    const confirmed = (list: readonly Question[]) =>
      list.filter((q) => q.status === 'CONFIRMED').length;
    return {
      confirmed: confirmed(questions),
      total: questions.length,
      prefilledConfirmed: confirmed(prefilled),
      prefilled: prefilled.length,
    };
  });

  constructor() {
    effect(() => {
      this.refresh.tick();
      void this.load(this.id());
    });
  }

  protected percent(part: number, total: number): number {
    return total === 0 ? 0 : Math.round((part / total) * 100);
  }

  protected openCount(section: QuestionnaireSection): number {
    return section.questions.filter((q) => q.status !== 'CONFIRMED').length;
  }

  protected stepStatus(section: QuestionnaireSection): string {
    const open = this.openCount(section);
    if (open === 0) return 'questionnaire.stepDone';
    const missing = section.questions.some((q) => q.status === 'MISSING');
    return plural(missing ? 'questionnaire.stepOpen' : 'questionnaire.stepToConfirm', open);
  }

  protected isAmountSection(section: QuestionnaireSection): boolean {
    return section.questions.every((q) => q.options.length === 0);
  }

  /** Back/next move focus to the new step heading, so keyboard and screen reader users land on it. */
  protected goTo(index: number): void {
    this.step.set(index);
    afterNextRender(() => this.stepHeading()?.nativeElement.focus(), { injector: this.injector });
  }

  protected async confirm(question: Question): Promise<void> {
    await this.run(() => this.api.confirmAnswer(this.id(), question.key));
  }

  protected async save(question: Question, value: string): Promise<void> {
    if (value === '' || value === question.value) return;
    await this.run(() => this.api.setAnswer(this.id(), question, value));
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
      const [questionnaire, assessment] = await Promise.all([
        this.api.getQuestionnaire(id),
        // The rule results are a supplement on this screen; without an assessment the questions still work.
        this.api.getAssessment(id).catch(() => null),
      ]);
      this.questionnaire.set(questionnaire);
      this.assessment.set(assessment);
      this.error.set(null);
    } catch (e) {
      this.error.set(
        inlineError(e, (detail) => this.translate.instant('questionnaire.loadFailed', { detail })),
      );
    }
  }
}
