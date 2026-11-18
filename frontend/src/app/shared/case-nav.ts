import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

/** Segmented navigation through one client case: documents, profiling, dashboard, advisory. */
@Component({
  selector: 'app-case-nav',
  imports: [RouterLink, RouterLinkActive, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="case-nav" [attr.aria-label]="'nav.case' | translate">
      <ul>
        <li>
          <a routerLink="/" [queryParams]="{ client: clientId() }">{{
            'nav.documents' | translate
          }}</a>
        </li>
        <li>
          <a
            [routerLink]="['/clients', clientId(), 'profilierung']"
            routerLinkActive="active"
            ariaCurrentWhenActive="page"
            >{{ 'nav.profilierung' | translate }}</a
          >
        </li>
        <li>
          <a
            [routerLink]="['/clients', clientId()]"
            routerLinkActive="active"
            ariaCurrentWhenActive="page"
            [routerLinkActiveOptions]="{ exact: true }"
            >{{ 'nav.dashboard' | translate }}</a
          >
        </li>
        <li>
          <a
            [routerLink]="['/clients', clientId(), 'beratung']"
            routerLinkActive="active"
            ariaCurrentWhenActive="page"
            >{{ 'nav.beratung' | translate }}</a
          >
        </li>
      </ul>
    </nav>
  `,
})
export class CaseNav {
  readonly clientId = input.required<string>();
}
