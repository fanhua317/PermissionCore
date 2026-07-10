import { compactHandleSummary, healthThresholds, intEnv } from './common.js';
import { runReadMix, setup as readSetup } from './read-mix.js';

const healthyRps = intEnv('HEALTHY_RPS', 50);
const soakRate = Math.max(1, Math.floor(healthyRps * 0.7));

export const options = {
  scenarios: {
    soak: {
      executor: 'constant-arrival-rate',
      rate: soakRate,
      timeUnit: '1s',
      duration: __ENV.DURATION || '30m',
      preAllocatedVUs: intEnv('PREALLOCATED_VUS', Math.max(20, Math.ceil(soakRate / 5))),
      maxVUs: intEnv('MAX_VUS', Math.max(100, soakRate * 2)),
      gracefulStop: '15s',
    },
  },
  thresholds: healthThresholds,
};

export function setup() {
  return readSetup();
}

export default function (data) {
  runReadMix(data);
}

export function handleSummary(data) {
  return compactHandleSummary(data);
}
