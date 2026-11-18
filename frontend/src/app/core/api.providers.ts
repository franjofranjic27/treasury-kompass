import { Provider } from '@angular/core';
import { readUseMock } from './data-mode';
import { HttpTreasuryApi } from './http-treasury-api';
import { MockTreasuryApi } from './mock-treasury-api';
import { TreasuryApi } from './treasury-api';

export function provideTreasuryApi(): Provider {
  return { provide: TreasuryApi, useClass: readUseMock() ? MockTreasuryApi : HttpTreasuryApi };
}
