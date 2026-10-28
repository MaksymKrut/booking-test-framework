# booking-test-framework

Java test automation framework for a containerized booking system under test
(https://github.com/uekita/fullstack-booking-system).

## Stack
- Java 21 (compiled with release 21), Maven, JUnit 5 (Jupiter), Playwright for Java,
  REST Assured, Jackson, Allure 3, SLF4J + Logback.
- Versions are pinned in pom.xml. Do not add dependencies without asking.

## Layout
- `src/main/java/com/booking/tests` — framework library:
  `config` (Config, PropertiesConfig) · `api/spec` (request/response specs, logging filter) ·
  `api/client` (one client per resource) · `api/model` (records) · `data` (builders) ·
  `ui/driver` (PlaywrightManager, JUnit extension) · `ui/pages` · `ui/components` · `util`.
- `src/test/java/com/booking/tests` — tests only (`api`, `ui`).
- Config: `src/main/resources/config/defaults.properties`; any key overridable with `-Dkey=value`.

## Conventions
- Records for DTOs; no Lombok.
- Page objects hold locators and actions, never assertions.
- Locators: id first, then role/label, then `tr[data-id]`; no XPath by index.
- Waits: Playwright auto-wait plus explicit `waitFor`/`waitForResponse`; never `Thread.sleep`.
- Every test creates its own data through the API; no dependency on seed rows or test order.
- Allure: `Allure.step(...)` in page objects and clients; screenshot + trace attached on failure.
- Logging through SLF4J; every HTTP call logged (method, URL, status, time).

## How we work (human in the loop)
- You draft; I review every diff before it is accepted.
- Propose changes as a short plan first; implement only what I approve.
- Never run `git add`, `git commit` or `git push` — I commit by hand.
- Never create files I did not ask for.
- A test counts only after I have seen it fail for the right reason (mutation check).
- Use the Playwright MCP server to inspect the running app at http://localhost:3000
  before proposing locators; verify every locator against the live DOM.

## Run
- App: `docker compose up -d` in the AUT repo; health at http://localhost:8080/actuator/health.
- Tests: `mvn clean test` · report: `mvn allure:serve`.