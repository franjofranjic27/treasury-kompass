import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Icon, IconName } from './icon';

export type BadgeTone = 'ok' | 'warn' | 'crit' | 'neutral' | 'pending' | 'new';

const TONE_ICON: Readonly<Record<BadgeTone, IconName>> = {
  ok: 'check',
  warn: 'alert',
  crit: 'cross',
  neutral: 'minus',
  pending: 'progress',
  new: 'plus',
};

/**
 * Status chip. Status is never conveyed by colour alone: every tone comes with an icon,
 * and the projected content is the text label.
 */
@Component({
  selector: 'app-badge',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'badge', '[attr.data-tone]': 'tone()' },
  template: `
    <app-icon [name]="iconName()" [size]="16" [strokeWidth]="strokeWidth()" />
    <span><ng-content /></span>
  `,
})
export class Badge {
  readonly tone = input.required<BadgeTone>();
  readonly icon = input<IconName>();

  protected readonly iconName = computed(() => this.icon() ?? TONE_ICON[this.tone()]);
  protected readonly strokeWidth = computed(() =>
    this.iconName() === 'check' || this.iconName() === 'progress' ? 2 : 2.5,
  );
}
