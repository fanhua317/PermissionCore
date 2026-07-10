import http from 'k6/http';
import { check, fail } from 'k6';
import { Trend } from 'k6/metrics';
import {
  BASE_URL,
  ACTOR_USERNAME,
  assertApiSuccess,
  authParams,
  compactHandleSummary,
  intEnv,
  login,
  requiredPassword,
} from './common.js';

const globalMutationDuration = new Trend('global_permission_mutation_duration', true);

export const options = {
  scenarios: {
    global_permission_mutation: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: intEnv('ITERATIONS', 10),
      maxDuration: __ENV.MAX_DURATION || '10m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  const tokens = login(ACTOR_USERNAME, requiredPassword());
  const params = authParams(tokens.accessToken, 'global_write_setup');
  const rolesBody = assertApiSuccess(
    http.get(`${BASE_URL}/api/role/list`, params),
    'global write setup roles',
  );
  const permissionsBody = assertApiSuccess(
    http.get(`${BASE_URL}/api/permission/list`, params),
    'global write setup permissions',
  );
  const perfRoles = (rolesBody.data || []).filter((item) =>
    typeof item.roleKey === 'string' && item.roleKey.indexOf('PERF_ROLE_') === 0);
  const role = perfRoles.length > 1 ? perfRoles[1] : null;
  const permission = (permissionsBody.data || []).find((item) =>
    typeof item.permKey === 'string' && item.permKey.indexOf('perf:generated:') === 0);
  if (!role || !permission) {
    fail('global permission setup requires seeded role and permission');
  }
  return { roleId: role.id, permissionId: permission.id };
}

export default function (data) {
  // Every global authorization mutation invalidates the current access token,
  // therefore this intentionally logs in once per sequential iteration.
  const tokens = login(ACTOR_USERNAME, requiredPassword());
  const response = http.put(
    `${BASE_URL}/api/role/${data.roleId}/permissions`,
    JSON.stringify({ permissionIds: [data.permissionId] }),
    authParams(tokens.accessToken, 'global_permission_mutation'),
  );
  globalMutationDuration.add(response.timings.duration);
  check(response, {
    'global permission mutation: HTTP 200': (r) => r.status === 200,
    'global permission mutation: API code 200': (r) => {
      try { return r.json('code') === 200; } catch (_) { return false; }
    },
  });
}

export function handleSummary(data) {
  return compactHandleSummary(data);
}
