/** Resolves after `ms` milliseconds; used for polling and for the simulated latency of the demo backend. */
export const delay = (ms: number): Promise<void> =>
  new Promise((resolve) => setTimeout(resolve, ms));
