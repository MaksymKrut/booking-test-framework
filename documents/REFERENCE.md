# REFERENCE — tools in this project: role, setup, use

## Runtime and build

| Tool | Role | Setup | How we use it |
|---|---|---|---|
| Java 21 (Temurin 25 installed) | Language/runtime | `pom.xml` `<maven.compiler.release>21</maven.compiler.release>`; `.vscode/settings.json` points at Temurin 25 | Code targets 21 (records, `var`, switch expressions, text blocks); any JDK ≥ 21 runs it |
| Maven 3.9+ | Build, dependency download, test entry point | `pom.xml`; libraries cached in `~/.m2/repository` | `mvn clean test` · `mvn test -Dgroups=api` · `mvn test -Dtest=ClassName` · `mvn -q dependency:resolve` |
| maven-surefire-plugin 3.6.1 | Maven's built-in test runner — runs when you type `mvn test` | pinned in `pom.xml`; passes `allure.results.directory` as a system property; pom property `excludedGroups=bugs` skips bug tests by default | Nothing to call directly; honours JUnit tags via `-Dgroups` / `-DexcludedGroups`; `-Dsurefire.rerunFailingTestsCount=1` reruns failures |
| maven-compiler-plugin 3.16.0 | Compiles with `release 21`, keeps parameter names | `pom.xml`, `<parameters>true</parameters>` | Nothing to call |
| exec-maven-plugin 3.6.4 | Runs a Java main from Maven | `pom.xml` | Browser install (optional: Playwright downloads Chromium on the first run by itself; use this when that download is blocked): `mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"` |
| Docker Desktop + Compose v2 | Runs the application under test | the application's own `docker-compose.yml` (sibling folder) | `docker compose up --build -d` · `docker compose ps` · reset `docker compose down -v` · health `curl localhost:8080/actuator/health` |

## Test framework

| Tool | Role | Setup | How we use it |
|---|---|---|---|
| JUnit 5 (Jupiter 6.1.3 via `junit-bom`) | Test framework: `@Test`, lifecycle, tags, parameterized tests, extensions, parallelism | `junit-jupiter-api` compile scope (framework code uses it), `junit-jupiter` + `junit-platform-launcher` test scope; `src/test/resources/junit-platform.properties` | `@Tag("api"/"ui"/"smoke")` on classes, `@Tag("bugs")` on bug tests (expected to fail); `@EnumSource` for the booking rule matrix; `@ParameterizedTest` + `@CsvSource`/`@MethodSource` for matrices; `@ExtendWith(UiTestExtension.class)` on `BaseUiTest`; `assertEquals`/`assertAll`/`assertTrue` for API assertions; parallel: classes concurrent on 2 threads, methods sequential; `@ResourceLock`/`@Isolated` if a test must run alone |
| Playwright for Java 1.64.0 | Drives Chromium for UI tests | `playwright` dependency; browser downloaded on first run (exec plugin as fallback); `ui.browser`, `ui.headless`, `ui.base.url` in config | `PlaywrightManager`: one `Playwright` + `Browser` per thread (ThreadLocal), one `BrowserContext` + `Page` per test, tracing on per context; locators `page.locator("#id")`, `getByRole(...).setExact(true)`, `tr[data-id='n']`; waits: auto-wait on actions, `locator.waitFor()`, `page.waitForResponse()`; assertions `assertThat(locator).hasText(...)` (retrying); `page.onDialog(Dialog::accept)` before native confirms; failure artifacts: `page.screenshot()` + `context.tracing().stop(path)` |
| REST Assured 6.3.0 | HTTP calls and response assertions for API tests | `rest-assured` compile scope; one `RestAssuredConfig` in `Specs` (Jackson 2 pinned, `Json.mapper()`, `LogConfig.blacklistHeader("Authorization")`) attached to each request spec, not to the global `RestAssured.config` | `given().spec(Specs.request()).body(record).when().post("/bookings").then().spec(Specs.created()).extract().as(Booking.class)`; `Specs.request()` = base URI, JSON headers, Allure + SLF4J filters; `Specs.status(n)` = status + JSON + `expectResponseTime(lessThan(ms))` |
| json-schema-validator 6.3.0 | Validates response shape against a JSON schema | test scope; schemas in `src/test/resources/schemas/` | `.body(matchesJsonSchemaInClasspath("schemas/booking.schema.json"))` |
| Hamcrest (transitive via REST Assured) | Matchers used by REST Assured specs | nothing | `lessThan(...)` in the response-time spec; `equalTo`, `hasKey` in `.body(...)` checks |
| Jackson 2.22.4 (databind + jsr310) | JSON ↔ Java records | `Json.mapper()` with `JavaTimeModule`, ISO instants, unknown properties ignored, nulls kept; handed to REST Assured in `Specs` | DTOs are records in `api/model`, response records annotated `@JsonIgnoreProperties(ignoreUnknown = true)`; `Instant` fields parse ISO-8601 with `Z` |

## Reporting and logging

| Tool | Role | Setup | How we use it |
|---|---|---|---|
| Allure 3.0.1 (`allure-jupiter`, `allure-rest-assured` via `allure-bom`) | Test report: steps, attachments, HTTP exchanges, history | `allure-jupiter` test scope registers itself as a JUnit listener automatically; `src/test/resources/allure.properties` → `target/allure-results` | `Allure.step("Book shift " + id, () -> ...)` in page objects and clients (lambdas, no AspectJ agent); `new AllureRestAssured()` filter in `Specs.request()` attaches request/response; `Allure.attachment(name, type, stream, AttachmentOptions.withFileExtension(ext))` (Allure 3 API; 2.x `addAttachment` is gone) for screenshot and trace in `UiTestExtension` |
| allure-maven 3.1.0 | Builds and opens the HTML report | `pom.xml` plugin; downloads the Allure CLI itself | `mvn allure:serve` (opens browser) · `mvn allure:report` → `target/site/allure-maven-plugin/index.html` |
| SLF4J 2.0.20 | Logging API | `slf4j-api` compile scope | `private static final Logger log = LoggerFactory.getLogger(X.class);` `log.info("...", args)` |
| Logback 1.6.6 | Logging implementation | `src/test/resources/logback.xml`: console + `target/logs/tests.log`; `com.booking.tests` and `http` at DEBUG | `Slf4jLogFilter` logs every HTTP call on logger `http`: `--> POST uri`, `<-- 201 POST uri (ms)`, bodies at DEBUG, headers never |

## Configuration

| Item | Where | Use |
|---|---|---|
| `defaults.properties` | `src/main/resources/config/` | `ui.base.url`, `api.base.url`, `ui.browser`, `ui.headless`, `api.max.response.ms` |
| Overrides | command line / environment | `-Dui.headless=false`, `-Dapi.base.url=...`, `UI_BASE_URL=...`; `-Denv=ci` adds `config/ci.properties` |
| `Config` / `PropertiesConfig` | `config/` | `Config.get().apiBaseUrl()` etc.; precedence `-D` > env var > env file > defaults |

## Repository and workflow

| Tool | Role | Setup | How we use it |
|---|---|---|---|
| Git + GitHub | History and submission | `.gitignore` excludes `target/`, `allure-results/`, logs, traces, `.claude/`, `.vscode/*` except `settings.json` | Commits by hand with descriptive messages; branch `master`; `git log --oneline` before every push |
| VS Code | Editor | Extension Pack for Java, XML (Red Hat), Container Tools | Test Runner for Java runs JUnit from the gutter; Container Tools shows the application's containers |
| Claude Code | AI pair under human-in-the-loop rules | `CLAUDE.md` (rules, layout, conventions), `.mcp.json` (Playwright MCP server) | Plan first, implement after approval, every diff reviewed; no git operations by the model; MCP lets the model inspect the running UI before proposing locators |
| `documents/` | Context for the model and for presentation | `ASSIGNMENT.md` (the brief), `BRIEFING.md` (application facts, framework spec, build plan), `PRESENT.md` (presentation notes), this file | Read at session start; checked for stale statements after every build step |

## Key commands (cheat sheet)

```
docker compose up --build -d                 # in the application repo
curl localhost:8080/actuator/health
mvn clean test
mvn test -Dgroups=api
mvn test -Dgroups=ui -Dui.headless=false
mvn test -Dtest=BookShiftE2ETest
mvn clean test                               # default: everything except bug tests (pom excludes "bugs")
mvn test -Dgroups=bugs -DexcludedGroups=     # only bug tests (expected red)
mvn test -DexcludedGroups=                   # everything, bug tests included
mvn test -Djunit.jupiter.execution.parallel.enabled=false
mvn allure:serve
mvn allure:report
```