import http from 'k6/http';
import { check } from 'k6';
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

const rate = intEnv('RATE', 50);
const duration = __ENV.DURATION || '30s';
const preAllocatedVUs = intEnv('PREALLOCATED_VUS', Math.max(20, Math.ceil(rate / 5)));
const maxVUs = intEnv('MAX_VUS', Math.max(100, rate * 2));

export const options = {
  discardResponseBodies: false,
  scenarios: {
    read_mix: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs,
      maxVUs,
      gracefulStop: '10s',
    },
  },
  thresholds: healthThresholds,
};

export function setup() {
  const tokens = login(ACTOR_USERNAME, requiredPassword());
  const rolesResponse = http.get(
    `${BASE_URL}/api/role/list`,
    authParams(tokens.accessToken, 'role_list_setup'),
  );
  const rolesBody = assertApiSuccess(rolesResponse, 'role list setup');
  const perfRoles = (rolesBody.data || []).filter((role) =>
    typeof role.roleKey === 'string' && role.roleKey.indexOf('PERF_ROLE_') === 0);

  return {
    accessToken: tokens.accessToken,
    inheritanceRoleId: perfRoles.length ? perfRoles[perfRoles.length - 1].id : 1,
    deepPage: (__ENV.PERF_SCALE || '10k') === '100k' ? 1000 : 100,
  };
}

function get(data, path, endpoint) {
  const response = http.get(
    `${BASE_URL}${path}`,
    authParams(data.accessToken, endpoint),
  );
  check(response, {
    [`${endpoint}: HTTP 200`]: (r) => r.status === 200,
    [`${endpoint}: API code 200`]: (r) => {
      try { return r.json('code') === 200; } catch (_) { return false; }
    },
  });
}

export function runReadMix(data) {
  const slot = ((__ITER + __VU * 17) % 100);
  if (slot < 25) {
    get(data, '/api/auth/info', 'auth_info');
  } else if (slot < 35) {
    get(data, '/api/auth/session-roles', 'session_roles');
  } else if (slot < 47) {
    get(data, '/api/user/page?pageNo=1&pageSize=20', 'user_page_first');
  } else if (slot < 53) {
    get(data, `/api/user/page?pageNo=${data.deepPage}&pageSize=100`, 'user_page_deep');
  } else if (slot < 59) {
    get(data, '/api/user/page?pageNo=1&pageSize=20&username=perf_user_00&nickname=perf_user_00', 'user_page_fuzzy');
  } else if (slot < 67) {
    get(data, '/api/role/list', 'role_list');
  } else if (slot < 75) {
    get(data, '/api/permission/tree', 'permission_tree');
  } else if (slot < 80) {
    get(data, `/api/role-inheritance/parents/${data.inheritanceRoleId}`, 'role_inheritance_parents');
  } else if (slot < 86) {
    get(data, '/api/dashboard/stats', 'dashboard_stats');
  } else if (slot < 90) {
    get(data, '/api/dashboard/recent-logins', 'dashboard_recent_logins');
  } else if (slot < 94) {
    get(data, '/api/dashboard/recent-operations', 'dashboard_recent_operations');
  } else if (slot < 97) {
    get(data, '/api/login-log/page?pageNo=1&pageSize=20&startTime=2026-01-01&endTime=2026-01-31', 'login_log_range');
  } else {
    get(data, '/api/oper-log/page?pageNo=1&pageSize=20&startTime=2026-01-01&endTime=2026-01-31', 'oper_log_range');
  }
}

export default function (data) {
  runReadMix(data);
}

export function handleSummary(data) {
  return compactHandleSummary(data);
}
