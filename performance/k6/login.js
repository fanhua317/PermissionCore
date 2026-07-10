import {
  compactHandleSummary,
  healthThresholds,
  intEnv,
  login,
  requiredPassword,
} from './common.js';

const vus = intEnv('VUS', 1);
export const options = {
  scenarios: {
    login: {
      executor: 'constant-vus',
      vus,
      duration: __ENV.DURATION || '30s',
      gracefulStop: '10s',
    },
  },
  thresholds: healthThresholds,
};

export default function () {
  const username = `perf_login_${String(((__VU - 1) % 200) + 1).padStart(4, '0')}`;
  login(username, requiredPassword());
}

export function handleSummary(data) {
  return compactHandleSummary(data);
}
