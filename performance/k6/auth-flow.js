import http from 'k6/http';
import { check } from 'k6';
import {
  BASE_URL,
  assertApiSuccess,
  authParams,
  compactHandleSummary,
  healthThresholds,
  intEnv,
  login,
  requiredPassword,
} from './common.js';

const vus = intEnv('VUS', 5);
export const options = {
  scenarios: {
    auth_flow: {
      executor: 'constant-vus',
      vus,
      duration: __ENV.DURATION || '30s',
      gracefulStop: '10s',
    },
  },
  thresholds: healthThresholds,
};

let tokens = null;

function usernameForVu() {
  return `perf_login_${String(((__VU - 1) % 200) + 1).padStart(4, '0')}`;
}

export default function () {
  if (!tokens) {
    tokens = login(usernameForVu(), requiredPassword());
  }

  const infoResponse = http.get(
    `${BASE_URL}/api/auth/info`,
    authParams(tokens.accessToken, 'auth_info'),
  );
  assertApiSuccess(infoResponse, 'auth info');

  const rolesResponse = http.get(
    `${BASE_URL}/api/auth/session-roles`,
    authParams(tokens.accessToken, 'session_roles'),
  );
  assertApiSuccess(rolesResponse, 'session roles');

  const oldRefreshToken = tokens.refreshToken;
  const refreshResponse = http.post(
    `${BASE_URL}/api/auth/refresh`,
    JSON.stringify({ refreshToken: oldRefreshToken }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'auth_refresh' } },
  );
  const refreshBody = assertApiSuccess(refreshResponse, 'refresh');
  tokens = refreshBody.data;

  const replayResponse = http.post(
    `${BASE_URL}/api/auth/refresh`,
    JSON.stringify({ refreshToken: oldRefreshToken }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'auth_refresh_replay' },
      responseCallback: http.expectedStatuses(401),
    },
  );
  check(replayResponse, {
    'refresh replay: HTTP 401': (r) => r.status === 401,
  });
}

export function handleSummary(data) {
  return compactHandleSummary(data);
}
