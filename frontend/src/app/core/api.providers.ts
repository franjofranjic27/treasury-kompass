import {
  EnvironmentProviders,
  inject,
  makeEnvironmentProviders,
  provideAppInitializer,
} from '@angular/core';
import { DataMode } from './data-mode';
import { HttpTreasuryApi } from './http-treasury-api';
import { MockTreasuryApi } from './mock-treasury-api';
import { TreasuryApi } from './treasury-api';

/**
 * Settles the data mode before the first screen, then provides the matching API. The factory runs
 * on first injection, i.e. after the initializer, so a startup fallback to demo data is respected.
 */
export function provideTreasuryApi(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideAppInitializer(() => inject(DataMode).init()),
    {
      provide: TreasuryApi,
      useFactory: (): TreasuryApi =>
        inject(DataMode).useMock() ? new MockTreasuryApi() : new HttpTreasuryApi(),
    },
  ]);
}
