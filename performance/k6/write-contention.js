import http from 'k6/http';
import { check, fail } from 'k6';
import {
  BASE_URL,
  ACTOR_USERNAME,
  assertApiSuccess,
  authParams,
  compactHandleSummary,
  healthThresholds,
  intEnv,
  login,
  requiredPassword,
} from './common.js';

const vus = intEnv('VUS', 20);
export const options = {
  scenarios: {
    role_assignment_contention: {
      executor: 'constant-vus',
      vus,
      duration: __ENV.DURATION || '2m',
      gracefulStop: '15s',
    },
  },
  thresholds: healthThresholds,
};

export function setup() {
  const tokens = login(ACTOR_USERNAME, requiredPassword());
  const params = authParams(tokens.accessToken, 'write_setup');
  const usersResponse = http.get(
    `${BASE_URL}/api/user/page?pageNo=1&pageSize=100&username=perf_user_`,
    params,
  );
  const rolesResponse = http.get(`${BASE_URL}/api/role/list`, params);
  const usersBody = assertApiSuccess(usersResponse, 'write setup users');
  const rolesBody = assertApiSuccess(rolesResponse, 'write setup roles');
  const userIds = ((usersBody.data && usersBody.data.records) || []).map((user) => user.id);
  const roleIds = (rolesBody.data || [])
    .filter((role) => typeof role.roleKey === 'string' && role.roleKey.indexOf('PERF_ROLE_') === 0)
    .slice(0, 20)
    .map((role) => role.id);
  if (!userIds.length || !roleIds.length) {
    fail('write contention setup requires seeded users and roles');
  }
  return { accessToken: tokens.accessToken, userIds, roleIds };
}

export default function (data) {
  const userId = data.userIds[(__VU + __ITER) % data.userIds.length];
  const roleId = data.roleIds[(__VU * 7 + __ITER) % data.roleIds.length];
  const response = http.put(
    `${BASE_URL}/api/user/${userId}/roles`,
    JSON.stringify({ roleIds: [roleId] }),
    authParams(data.accessToken, 'user_assign_roles'),
  );
  check(response, {
    'assign role: HTTP 200': (r) => r.status === 200,
    'assign role: API code 200': (r) => {
      try { return r.json('code') === 200; } catch (_) { return false; }
    },
  });
}

export function handleSummary(data) {
  return compactHandleSummary(data);
}
