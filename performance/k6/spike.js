import { compactHandleSummary, healthThresholds, intEnv } from './common.js';
import { runReadMix, setup as readSetup } from './read-mix.js';

const healthyRps = intEnv('HEALTHY_RPS', 50);
const normalRate = Math.max(1, Math.floor(healthyRps * 0.7));

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-arrival-rate',
      startRate: normalRate,
      timeUnit: '1s',
      preAllocatedVUs: intEnv('PREALLOCATED_VUS', Math.max(50, normalRate)),
      maxVUs: intEnv('MAX_VUS', Math.max(200, healthyRps * 4)),
      stages: [
        { target: normalRate, duration: __ENV.RECOVERY_BEFORE || '30s' },
        { target: healthyRps * 2, duration: __ENV.SPIKE_DURATION || '60s' },
        { target: normalRate, duration: __ENV.RECOVERY_AFTER || '2m' },
      ],
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
