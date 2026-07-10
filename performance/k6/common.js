import http from 'k6/http';
import { check, fail } from 'k6';

export const BASE_URL = (__ENV.BASE_URL || 'http://host.docker.internal:15432').replace(/\/$/, '');
export const ACTOR_USERNAME = __ENV.PERF_TEST_USERNAME || 'perf_login_0001';

export function intEnv(name, fallback) {
  const parsed = parseInt(__ENV[name] || `${fallback}`, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

export function requiredPassword() {
  const password = __ENV.PERF_ADMIN_PASSWORD;
  if (!password) {
    fail('PERF_ADMIN_PASSWORD must be passed to the k6 container by environment name.');
  }
  return password;
}

export function jsonBody(response, operation) {
  try {
    return response.json();
  } catch (error) {
    fail(`${operation}: response is not valid JSON (HTTP ${response.status})`);
    return null;
  }
}

export function assertApiSuccess(response, operation) {
  const body = jsonBody(response, operation);
  const ok = check(response, {
    [`${operation}: HTTP 200`]: (r) => r.status === 200,
    [`${operation}: API code 200`]: () => body && body.code === 200,
  });
  if (!ok) {
    fail(`${operation} failed (HTTP ${response.status}, API code ${body && body.code})`);
  }
  return body;
}

export function login(username = ACTOR_USERNAME, password = requiredPassword()) {
  const response = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username, password }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'auth_login' },
    },
  );
  const body = assertApiSuccess(response, 'login');
  if (!body.data || !body.data.accessToken || !body.data.refreshToken) {
    fail('login: token fields are missing');
  }
  return body.data;
}

export function authParams(accessToken, endpoint, extra) {
  const params = extra || {};
  params.headers = Object.assign({}, params.headers || {}, {
    Authorization: `Bearer ${accessToken}`,
    'Content-Type': 'application/json',
  });
  params.tags = Object.assign({}, params.tags || {}, { endpoint });
  return params;
}

function metricValues(data, metricName) {
  const metric = data.metrics[metricName];
  return metric && metric.values ? metric.values : {};
}

function numberOrNull(value) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

export function buildCompactSummary(data) {
  const duration = metricValues(data, 'http_req_duration');
  const requests = metricValues(data, 'http_reqs');
  const failures = metricValues(data, 'http_req_failed');
  const checks = metricValues(data, 'checks');
  const iterations = metricValues(data, 'iterations');
  const dropped = metricValues(data, 'dropped_iterations');
  const received = metricValues(data, 'data_received');
  const sent = metricValues(data, 'data_sent');

  return {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    runId: __ENV.RUN_ID || 'manual',
    scenario: __ENV.SCENARIO_NAME || 'unknown',
    scale: __ENV.PERF_SCALE || 'unknown',
    targetRps: intEnv('RATE', 1),
    metrics: {
      requestCount: numberOrNull(requests.count),
      requestRate: numberOrNull(requests.rate),
      iterationCount: numberOrNull(iterations.count),
      iterationRate: numberOrNull(iterations.rate),
      droppedIterations: numberOrNull(dropped.count),
      errorRate: numberOrNull(failures.rate),
      checkRate: numberOrNull(checks.rate),
      p50Ms: numberOrNull(duration.med),
      p90Ms: numberOrNull(duration['p(90)']),
      p95Ms: numberOrNull(duration['p(95)']),
      p99Ms: numberOrNull(duration['p(99)']),
      avgMs: numberOrNull(duration.avg),
      maxMs: numberOrNull(duration.max),
      receivedBytes: numberOrNull(received.count),
      sentBytes: numberOrNull(sent.count),
    },
  };
}

export function compactHandleSummary(data) {
  const summary = buildCompactSummary(data);
  const serialized = `${JSON.stringify(summary, null, 2)}\n`;
  const output = {};
  output.stdout = `\nPERF_SUMMARY ${JSON.stringify(summary)}\n`;
  if (__ENV.SUMMARY_PATH) {
    output[__ENV.SUMMARY_PATH] = serialized;
  }
  return output;
}

export const healthThresholds = {
  checks: ['rate>0.99'],
  http_req_failed: ['rate<0.01'],
  http_req_duration: ['p(95)<200', 'p(99)<500'],
};
