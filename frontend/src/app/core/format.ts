import { Pipe, PipeTransform } from '@angular/core';

const formatters = new Map<number, Intl.NumberFormat>();

function formatter(decimals: number): Intl.NumberFormat {
  let nf = formatters.get(decimals);
  if (!nf) {
    nf = new Intl.NumberFormat('de-CH', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
    formatters.set(decimals, nf);
  }
  return nf;
}

/** Swiss number format with straight apostrophes as thousands separator. */
export function formatNumber(value: number, decimals = 1): string {
  return formatter(decimals).format(value).replace(/[’’]/g, "'").replace('-', '−');
}

/** Formats a value given in millions as «12.3 Mio.». */
@Pipe({ name: 'mio' })
export class MioPipe implements PipeTransform {
  transform(value: number | null | undefined, decimals = 1): string {
    return value === null || value === undefined ? '–' : `${formatNumber(value, decimals)} Mio.`;
  }
}

/** Formats a value given in millions as full CHF amount, e.g. «CHF 1'395'000». */
@Pipe({ name: 'chf' })
export class ChfPipe implements PipeTransform {
  transform(valueInMillions: number | null | undefined): string {
    return valueInMillions === null || valueInMillions === undefined
      ? '–'
      : `CHF ${formatNumber(Math.round(valueInMillions * 1_000_000), 0)}`;
  }
}

/** Formats a ratio (0.58) or percentage number as «58 %». */
@Pipe({ name: 'pct' })
export class PctPipe implements PipeTransform {
  transform(value: number | null | undefined, isRatio = true, decimals = 0): string {
    if (value === null || value === undefined) return '–';
    return `${formatNumber(isRatio ? value * 100 : value, decimals)} %`;
  }
}
