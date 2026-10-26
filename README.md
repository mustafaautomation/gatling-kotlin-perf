# gatling-kotlin-perf

[![CI](https://github.com/mustafaautomation/gatling-kotlin-perf/actions/workflows/ci.yml/badge.svg)](https://github.com/mustafaautomation/gatling-kotlin-perf/actions/workflows/ci.yml)

Performance tests written in Kotlin with [Gatling](https://gatling.io/), run as a gate in GitHub
Actions against a small Ktor shop service that lives in the same repo.

Personal portfolio project, not production or client work. The shop service exists only as a load
target.

## What is here

| Part | Details |
|---|---|
| Target service | Ktor 3.6 on Netty, Kotlin 2.4, JDK 21. In-memory catalogue, carts and checkout with exact `BigDecimal` totals and a stock lock so concurrent checkouts cannot oversell |
| Unit tests | 10 JUnit tests: domain rules, the HTTP API through Ktor's test host, a 300-thread oversell test, and the fault-injection switches |
| Gatling journeys | Browse (list + detail), then purchase: create cart, add 1 to 3 random items, check out, read the order back. Values are correlated between requests (`cartId`, `orderId`) and every response is checked with JMESPath, not just its status |
| `SmokeSimulation` | One user, fails on any error |
| `CheckoutLoadSimulation` | Open workload model: arrivals ramp to N new shoppers per second, then hold. Asserts global p95, checkout p99 and error percentages |

The rate, ramp, hold time and the SLO thresholds are system properties, so the same simulation runs
locally and in CI:

| Property | Default | Meaning |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | Target |
| `usersPerSec` / `rampSeconds` / `holdSeconds` | 10 / 20 / 60 | Load shape |
| `slo.p95Ms` | 250 | Global 95th percentile response time must be below this |
| `slo.checkoutP99Ms` | 500 | Checkout 99th percentile must be below this |
| `slo.maxErrorPercent` | 1 | Global and checkout error rates must be below this |

## What CI does

| Job | What it proves |
|---|---|
| Build and unit tests | Kotlin compiles with warnings as errors, unit tests pass, the shop jar and the simulations build |
| Smoke + load test | The service starts; the smoke run has zero errors; 20 new shoppers per second for a 20 s ramp and a 60 s hold meet every SLO assertion. Gatling's HTML reports are uploaded as an artifact and the assertion results go to the job summary |
| Load test against a degraded build | The same load against the shop started with 400 ms of extra checkout latency and every 20th checkout returning 503. The job passes only if Gatling fails **because of its assertions**, and specifically the p95 and checkout error-rate ones |

The negative job is there because a performance gate that never fails proves nothing. It makes sure
the thresholds are actually wired to the build result.

On a local run (Apple silicon laptop, October 2026), 20 shoppers per second produced about 3,500
requests in 30 s with a p95 of 4 ms and no errors, and the degraded build failed with a p95 of
411 ms and a 4.8% checkout error rate. CI numbers are in each run's job summary. The thresholds are
deliberately loose: shared GitHub runners are noisy, and the target here is a regression gate for an
in-memory service, not a capacity measurement.

## Run it locally

Requires JDK 21+ and Maven.

```
mvn verify                                   # unit tests + jar
scripts/start-app.sh                         # shop on :8080 (logs in app.log)
mvn gatling:test -Dgatling.simulationClass=simulations.SmokeSimulation
mvn gatling:test -Dgatling.simulationClass=simulations.CheckoutLoadSimulation -DusersPerSec=20 -DholdSeconds=30
scripts/stop-app.sh
```

To see the gate fail, start the shop with `SHOP_CHECKOUT_DELAY_MS=400 SHOP_FAIL_EVERY=20 scripts/start-app.sh`.
Reports land in `target/gatling/<simulation>-<timestamp>/index.html`.

## Limits

- The load generator and the service share one CI runner, so they compete for CPU. Results show
  regressions, not real capacity.
- The service is in-memory; there is no database, network hop or cache, which is where most real
  latency comes from.
- No distributed load or Gatling Enterprise features; this is the open-source Gatling engine only.

## License

MIT
