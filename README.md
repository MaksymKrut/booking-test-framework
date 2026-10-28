# booking-test-framework

Java test automation framework for a containerized courier shift booking system
(system under test: https://github.com/uekita/fullstack-booking-system).

One framework, two layers: REST Assured against the API and Playwright against the UI,
run by JUnit 5 in parallel, reported by Allure, logged through SLF4J. Built for a Senior
Quality Engineer technical exercise, with AI assistance under a human-in-the-loop workflow
(see below).

## How to use

### 1. Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | 21 or newer (project compiles with `release 21`) | `java -version` |
| Maven | 3.9 or newer | `mvn -v` |
| Docker Desktop | Compose v2 or newer | `docker compose version` |
| Free ports | 3000 (UI), 8080 (API), 5432 (Postgres) | — |

### 2. Start the application under test

In a folder of its own, not inside this project:

```
git clone https://github.com/uekita/fullstack-booking-system
cd fullstack-booking-system
docker compose up --build -d
```

The first build takes a few minutes. Verify:

```
curl localhost:8080/actuator/health
```

Expected: `{"status":"UP"}`. UI at http://localhost:3000, Swagger at
http://localhost:8080/swagger-ui.html.

To reset the application to its seed state: `docker compose down -v && docker compose up -d`.

### 3. Install the browser (once)

```
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```

### 4. Run the tests

Full suite (API + UI, headless, parallel):

```
mvn clean test
```

Subsets:

| Goal | Command |
|---|---|
| API tests only | `mvn test -Dgroups=api` |
| UI tests only | `mvn test -Dgroups=ui` |
| Smoke subset | `mvn test -Dgroups=smoke` |
| One class | `mvn test -Dtest=BookShiftE2ETest` |
| Watch the browser | `mvn test -Dgroups=ui -Dui.headless=false` |
| Sequential (debugging) | `mvn test -Djunit.jupiter.execution.parallel.enabled=false` |
| Other environment | `mvn test -Denv=ci` or `-Dapi.base.url=... -Dui.base.url=...` |
| Rerun failures once | `mvn test -Dsurefire.rerunFailingTestsCount=1` |

### 5. Generate and view the report

Interactive (builds the report and opens it in the browser):

```
mvn allure:serve
```

Static report, for CI artifacts or sharing:

```
mvn allure:report
```

Output: `target/site/allure-maven-plugin/index.html`. The report shows each test as named
steps, every API request and response, and for a failed UI test the screenshot and the
Playwright trace (`trace.zip`, open with `npx playwright show-trace <file>`).

### 6. Logs

Console during the run, and `target/logs/tests.log` after it. Every HTTP call is logged with
method, URL, status and time; bodies at DEBUG level; the `Authorization` header is redacted.

## Requirements map

| Exercise requirement | Where |
|---|---|
| Maven project, JUnit 5 runner | `pom.xml`, `src/test/resources/junit-platform.properties` |
| Separation of concerns: page objects, API clients, config, test data, utilities | `src/main/java/com/booking/tests/{ui/pages, api/client, config, data, util}` |
| UI journeys (1–2) with explicit waits, robust locators, no hardcoded data | `src/test/java/.../ui/`, locators and waits in `ui/pages` and `ui/components`, data from `data/` builders |
| API tests (1–2 endpoints): status, response time, JSON structure and data | `src/test/java/.../api/`, `ResponseSpecification` with time ceiling, `schemas/*.json` |
| Request / response specifications | `api/spec/Specs` |
| POJO (de)serialization | `api/model/` records via Jackson |
| Reporting | Allure: steps, attachments, HTTP exchanges — `mvn allure:serve` |
| Logging | SLF4J + Logback, `api/spec/Slf4jLogFilter`, `src/test/resources/logback.xml` |
| Test-data management and test independence | every test creates its own courier and shift via API |
| Bug reports | `BUGS.md` |

## What is covered
*(filled at the end: API tests, UI journeys, concurrency test — one line each)*

## Project structure

```
src/main/java/com/booking/tests/      framework library
  config/        Config interface + PropertiesConfig (file < env var < -D)
  api/spec/      Specs (RequestSpec / ResponseSpec), Slf4jLogFilter
  api/client/    one client per resource: CourierClient, ShiftClient, BookingClient
  api/model/     records: Courier, Shift, Booking, *Request, ApiError, enums
  data/          builders with sensible defaults; tests override only what they assert
  ui/driver/     PlaywrightManager (one browser per thread, context + page per test),
                 UiTestExtension (screenshot + trace attached on failure)
  ui/pages/      page objects: locators and actions, no assertions
  ui/components/ NavTabs, BookDialog, Toast — reused across pages
  util/          Waits (fluent polling for non-DOM conditions), Json
src/main/resources/config/            defaults.properties (+ per-environment files)
src/test/java/com/booking/tests/      tests only
  api/           API tests
  ui/            UI journeys
src/test/resources/                   junit-platform.properties, allure.properties,
                                      logback.xml, schemas/
```

Framework code lives in `src/main` because it is a library: compiled on its own
classpath, reusable by other suites, and the tests under `src/test` read as plain
business cases.

## Design principles

- Every test creates its own data through the API (unique courier, far-future shift);
  nothing depends on seed rows or on another test. Tests run in parallel by default.
- Page objects return the next page or component; a test reads as the user journey.
- Three tiers of waiting: Playwright auto-wait for actions, explicit `waitFor` /
  `waitForResponse` for semantic state, a small fluent poller for cross-layer conditions.
  No `Thread.sleep`.
- Locators: `id` first, then role/label, then `tr[data-id]`; never XPath by index.
- Assertions live in tests, never in page objects or clients.
- Every HTTP call is logged and attached to the report.
- Secrets never enter the repository or the logs.

## Choosing the tooling

| Choice | Picked | Over | Why |
|---|---|---|---|
| Build | Maven | Gradle | The exercise and the application itself are Maven; `mvn clean test` is what a reviewer will type. |
| Runner | JUnit 5 (Jupiter 6.x, same API) | TestNG | Default of the Spring Boot ecosystem; the extension model gives screenshot and trace on failure in one small class; parallel execution is a properties file, no plugin. |
| UI | Playwright for Java | Selenium | Auto-waiting on actionability removes most explicit-wait boilerplate; the trace viewer shows DOM, network and console at the failing step; browsers are bundled. Explicit waits are kept where the condition has business meaning. |
| API | REST Assured + JSON-schema validator | plain HttpClient | Request/response specifications hold base URL, headers, logging and the response-time ceiling in one place; schema validation pins the response shape. |
| JSON | Java records + Jackson | Lombok | Immutable DTOs with zero annotation processing; nothing to install in an IDE. |
| Reporting | Allure 3 | ExtentReports | Steps, screenshots, traces and every HTTP exchange in one report; history across runs. |
| Logging | SLF4J + Logback | Log4j2 | Standard API; one `logback.xml`; a REST Assured filter logs each call with the auth header redacted. |
| Config | Hand-rolled `Config` interface | Owner / Typesafe Config | Twenty lines; precedence is `-D` property, then environment variable, then `config/<env>.properties`. No unmaintained dependency. |
| Java | 21 | 25 | Matches the application and every library baseline; a one-property change later. |

## Tech stack

| Tool | Version |
|---|---|
| Java | 21 (release target; built with Temurin 25) |
| Maven / Surefire | 3.9+ / 3.6.1 |
| JUnit Jupiter | 6.1.3 |
| Playwright for Java | 1.64.0 |
| REST Assured + json-schema-validator | 6.3.0 |
| Jackson (databind, jsr310) | 2.22.4 |
| Allure (jupiter, rest-assured) / allure-maven | 3.0.1 / 3.1.0 |
| SLF4J / Logback | 2.0.20 / 1.6.6 |

## Human in the loop

This project was built with Claude Code as a pair, under rules committed to the
repository in `CLAUDE.md`:

- AI drafts, I decide. Every change is proposed as a short plan first, implemented only
  after approval, and every diff is reviewed before it is accepted.
- No AI commits. All commits are made by hand with my own messages; the history is mine.
- A test counts only after I have seen it fail for the right reason. Each business-rule
  test was mutated once (expected status flipped, rule broken) to confirm it catches what
  it claims to catch.
- AI-generated boilerplate (record DTOs, page-object scaffolding, README formatting) is
  reviewed the same way as hand-written code.
- Rejected suggestions are part of the record: proposals that would have introduced a
  brittle selector or a wrong expected status are noted under design decisions.

The intent: reproducible, reviewable AI assistance, with accountability resting on the
engineer.

## Configuration

`src/main/resources/config/defaults.properties`:

| Key | Default | Meaning |
|---|---|---|
| `ui.base.url` | `http://localhost:3000` | UI entry point |
| `api.base.url` | `http://localhost:8080/api` | API base path |
| `ui.browser` | `chromium` | `chromium`, `firefox` or `webkit` |
| `ui.headless` | `true` | headless browser |
| `api.max.response.ms` | `2000` | response-time ceiling enforced by the response spec |

Precedence: `-Dkey=value` > environment variable (`UI_BASE_URL`, ...) >
`config/<env>.properties` (via `-Denv=<name>`) > `defaults.properties`.

## Extending the framework

| Task | Steps |
|---|---|
| New page | add a class in `ui/pages` extending `BasePage`; locators by id/role; actions return the next page; no assertions |
| New endpoint | add a method to the resource's client in `api/client` using `Specs.request()`; add a record to `api/model` if the payload is new |
| New test | extend `BaseApiTest` or `BaseUiTest`; build data with `data/` builders; tag it `api`/`ui` and `smoke`/`regression` |
| Negative case | call the client's raw method, assert `Specs.status(409)` and `ApiError.message()` |
| Data-driven case | `@ParameterizedTest` with `@CsvSource` / `@MethodSource` over a builder override |
| Another environment | add `config/<name>.properties`, run with `-Denv=<name>` |

## Assumptions and known issues
*(completed at the end; already known:)*
- The application has no authentication, so the exercise's "login → booking" journey is
  implemented as "seed via API → book a shift in the UI → verify in Bookings and via API".
  An auth seam exists in the request specification for when a token appears.
- The UI exposes stable `id`s and `data-*` attributes but no `data-testid`; locators use
  id first, then role/label.
- Seed shifts are created relative to database start time and age out; tests never rely on
  them.
- The success toast auto-hides after four seconds and is asserted immediately after the
  action.
- The response-time ceiling (2000 ms) is sized for Docker on a laptop; it is an SLA hook,
  not a benchmark.

## Further work

**Scale-up**
- Tag-based suites (smoke / regression) with a CI matrix sharded by package.
- CI pipeline: Compose up with health wait, `mvn test`, Allure report as artifact,
  application logs on failure.
- Testcontainers to start the application from the suite itself.
- Allure history and trends; quarantine tag with a ticket for known-flaky tests.
- Contract layer: validate responses against the application's OpenAPI document
  (`/v3/api-docs`); consumer-driven contracts when front end and back end deploy
  independently.
- Load test on `POST /bookings` with k6; event assertions once booking events are
  published.
- Ask the application team for `data-testid` attributes.

**Deep AI integration**
- Playwright MCP server (wired in `.mcp.json`): the model inspects the running UI before
  proposing a locator; every locator is then verified against the live DOM.
- Test candidates generated from the OpenAPI document and the running application,
  human-verified before they count.
- Exploratory API probing through MCP feeding the bug log.
- Flaky-test triage assisted by the model over Allure history.
- AI review gate on pull requests under the same human-in-the-loop rules.
- LLM-as-judge evaluations with golden sets if the application gains an AI feature.

**Coverage**
- Cancel-booking journey, courier management journey, shift edit guard rails.
- Visual regression of the shifts table.

## Bug reports

See `BUGS.md` — reproduction steps, expected and actual behaviour, severity, evidence.

## License

MIT — see `LICENSE`.