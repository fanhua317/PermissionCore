# PermissionCore performance suite

This directory contains the reproducible load-test harness for the isolated
`docker-compose.perf.yml` stack. It never connects to the normal host MySQL
port and does not reuse the normal Docker volumes.

## Safety and prerequisites

- Docker Desktop must be running. The runner uses the official
  `grafana/k6:2.0.0` image.
- MySQL and Redis are reachable only inside the Compose network. The backend
  is bound to `127.0.0.1:15432`; Prometheus metrics are bound to
  `127.0.0.1:15433`.
- The backend runs `docker,perf`; only the perf profile enables the dedicated
  Prometheus endpoint. The normal business routes and `/api/health` contract
  are unchanged.
- Resource limits are fixed at backend 4 vCPU / 2 GiB with a 1 GiB JVM heap,
  MySQL 2 vCPU / 2 GiB with a 1 GiB InnoDB buffer pool, and Redis 1 vCPU /
  512 MiB with 384 MiB maxmemory. Hikari defaults to 20 connections unless an
  explicit experiment overrides it.
- All five secrets are required as process environment variables. They are
  passed to containers by environment name and are never written to SQL,
  summaries, CSV files, or Git.
- `Smoke`, `Baseline`, `Login`, `Auth`, `Writes`, `Soak`, `Spike`, and `All`
  remove their isolated containers and volumes by default. Add
  `-KeepEnvironment` only when debugging. `Prepare` intentionally leaves the
  environment running; use `-Action Down` when finished.

PowerShell example (replace values only in the current shell):

```powershell
$env:PERF_MYSQL_ROOT_PASSWORD = '<local-only value>'
$env:PERF_MYSQL_APP_PASSWORD  = '<local-only value>'
$env:PERF_REDIS_PASSWORD      = '<local-only value>'
$env:PERF_JWT_SECRET          = '<at least 32 random bytes>'
$env:PERF_ADMIN_PASSWORD      = '<local-only value>'
```

## Quick start

Run the 10k-user smoke test:

```powershell
.\performance\Invoke-PerformanceTest.ps1 -Action Smoke -Scale 10k
```

Prepare and retain an isolated environment for manual debugging:

```powershell
.\performance\Invoke-PerformanceTest.ps1 -Action Prepare -Scale 10k
# ...manual checks...
.\performance\Invoke-PerformanceTest.ps1 -Action Down
```

Run the full 10k matrix with the defined defaults (this includes the 30-minute
soak and therefore takes a long time). `All` deliberately stops before the
Login/Auth/write/soak stages if the read ladder finds no healthy tier:

```powershell
.\performance\Invoke-PerformanceTest.ps1 -Action All -Scale 10k
```

The ladder, repeats, warm-up and sampling duration are parameterized:

```powershell
.\performance\Invoke-PerformanceTest.ps1 `
  -Action Baseline -Scale 10k `
  -Rates 50,100,200,400,800 `
  -Repeats 3 -WarmupSeconds 30 -SampleSeconds 120
```

Individual matrices are available through `Login`, `Auth`, `Writes`,
`Consistency`, `Soak`, and `Spike`. `Consistency` starts the optional second
backend and proves cross-node token use, immediate authorization revocation,
and remote-login session replacement. `Soak` and `Spike` accept `-HealthyRps`, which must be the highest
healthy sustained tier from a completed baseline.

## Deterministic data

`Generate-SeedSql.ps1` expands `seed/seed-template.sql` into ignored `raw/`
files. It is safe to rerun in the isolated database and deletes only records
with the performance prefixes.

| Scale | Users | Departments | Roles | Permissions | Login logs | Operation logs |
|---|---:|---:|---:|---:|---:|---:|
| `10k` | 10,000 | 100 | 100 | 300 | 20,000 | 20,000 |
| `100k` | 100,000 | 500 | 200 | 1,000 | 200,000 | 200,000 |

Exactly 200 accounts named `perf_login_0001` through `perf_login_0200` copy
the runtime bootstrap administrator's BCrypt hash. No password is embedded in
the seed. They receive a dedicated least-privilege load role instead of the
administrator role, preventing the test token from expanding every generated
permission into an oversized HTTP header. Other synthetic users contain an intentionally non-login marker.
Synthetic values use reserved example domains and documentation-only IP
ranges.

## Scenarios and results

- `read-mix.js`: access-token validation, current user/session roles, normal,
  deep and fuzzy user pages, role/permission trees, recursive inheritance,
  dashboard and time-range log queries.
- `login.js`: BCrypt login at `1/5/10/20/40` VUs, kept separate from read RPS.
- `auth-flow.js`: login, info, session roles, atomic refresh and replay rejection.
- `write-contention.js`: concurrent role assignments to different users.
- `global-permission.js`: sequential global permission mutations, with a fresh
  administrator token per mutation because every mutation revokes old tokens.
- `soak.js` and `spike.js`: 70% healthy-RPS soak, followed by a 2x spike and
  recovery profile.

Each run writes a compact `summary/*.json`, combined `summary.csv`,
`environment.json`, and (for a ladder) `ladder.csv`. Per-sample Prometheus,
Docker, MySQL and Redis CSV files plus MySQL top statement digests live below
`results/<run>/raw/` and are intentionally ignored because they can be large.

The harness classifies a tier as healthy only when median error rate is below
1%, median P95 is at most 200 ms, median P99 is at most 500 ms, no three
consecutive CPU samples exceed 85%, and no three consecutive Hikari samples
show pending connections. This is a controlled capacity boundary, not a
production SLA.

## Published result boundary

Under the fixed environment and the health rule above, the confirmed highest
healthy tier for the 10k-user read mix is **200 RPS**. This is a reproducible
controlled-host result, not a production SLA or an instantaneous peak claim.

The 100k-user read mix remains constrained by the user search that preserves
leading-wildcard `%term%` contains semantics. A normal B-tree cannot optimize
that predicate, so no healthy 100k read tier is published. Do not turn partial
100k runs or older raw result directories into a generic capacity claim.

See [PERFORMANCE_REPORT.md](../PERFORMANCE_REPORT.md) for the reviewed evidence,
bottleneck analysis, limitations, and resume-safe wording. Only compact
artifacts linked by that report should be cited; `results/*/raw/` is diagnostic
data and remains ignored.
