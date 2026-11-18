import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Stroke icons on a 20 × 20 grid, taken from the design mockups. Circles and rects are written as paths. */
const ICONS = {
  check: ['M4 10.5l4 4 8-9'],
  'chevron-right': ['M7 4l6 6-6 6'],
  'chevron-down': ['M4 7l6 6 6-6'],
  plus: ['M10 3v14', 'M3 10h14'],
  search: ['M14.5 8.5a6 6 0 1 1-12 0a6 6 0 1 1 12 0', 'M13 13l5 5'],
  user: ['M13.5 7a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0', 'M3.5 18a6.5 6.5 0 0 1 13 0'],
  upload: ['M10 13V2.5', 'M5.5 7L10 2.5 14.5 7', 'M3 12.5v5h14v-5'],
  file: ['M4 1.5h8l4 4v13H4z', 'M12 1.5v4h4', 'M7 11h6', 'M7 14h6'],
  document: ['M4 2h8l4 4v12H4z', 'M12 2v4h4'],
  progress: ['M10 3a7 7 0 1 1-7 7'],
  alert: ['M10 3.5v8', 'M10 14.5v2'],
  cross: ['M5 5l10 10', 'M15 5L5 15'],
  minus: ['M5 10h10'],
  info: ['M18 10a8 8 0 1 1-16 0a8 8 0 1 1 16 0', 'M10 9v5', 'M10 6v1.5'],
  chat: ['M2 3h16v11H8l-4 3v-3H2z'],
  lock: ['M4 9h12v9H4z', 'M7 9V6a3 3 0 0 1 6 0v3'],
  'arrow-up': ['M10 17V4', 'M4.5 9.5L10 4l5.5 5.5'],
} as const satisfies Record<string, readonly string[]>;

export type IconName = keyof typeof ICONS;

/** Decorative inline SVG icon; the meaning is always carried by adjacent text. */
@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'icon' },
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      [attr.stroke-width]="strokeWidth()"
      aria-hidden="true"
      focusable="false"
    >
      @for (d of paths(); track $index) {
        <path [attr.d]="d"></path>
      }
    </svg>
  `,
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly size = input(20);
  readonly strokeWidth = input(2);

  protected readonly paths = computed(() => ICONS[this.name()]);
}
