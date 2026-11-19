import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { BackendNoticeKind, DataMode } from '../core/data-mode';
import { Icon } from '../shared/icon';

/**
 * Shell-wide notice about the backend: a live request found none (with the way out to demo data),
 * or a probe found none and the app shows demo data, at startup or after the advisor tried to leave
 * demo mode. Rendered once, above the routed screen.
 */
@Component({
  selector: 'app-backend-notice',
  imports: [TranslatePipe, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (notice() === 'unavailable') {
      <div class="banner banner-crit" role="alert" data-testid="backend-error">
        <app-icon name="alert" />
        <span>
          <b>{{ 'backend.unavailableTitle' | translate }}</b>
          {{ 'backend.unavailableText' | translate }}
        </span>
        <button
          class="btn btn-secondary"
          type="button"
          data-testid="switch-to-mock"
          (click)="dataMode.useDemoData()"
        >
          {{ 'backend.switchToMock' | translate }}
        </button>
      </div>
    } @else {
      <div class="banner" role="alert" data-testid="backend-error">
        <app-icon name="info" />
        <span>
          <b>{{ 'backend.probeFailedTitle' | translate }}</b>
          @if (notice() === 'startupFallback') {
            {{ 'backend.startupFallbackText' | translate }}
          } @else {
            {{ 'backend.probeFailedText' | translate }}
          }
        </span>
        <button class="btn-link" type="button" (click)="dataMode.dismissNotice()">
          {{ 'backend.dismiss' | translate }}
        </button>
      </div>
    }
  `,
})
export class BackendNotice {
  readonly notice = input.required<BackendNoticeKind>();

  protected readonly dataMode = inject(DataMode);
}
