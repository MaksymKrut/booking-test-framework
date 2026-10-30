# Presentation notes — booking-test-framework

## How this was built
- With Claude Code as a pair, human in the loop. The rules are in CLAUDE.md and they are the method, not a disclaimer.
- AI drafts, I decide: plan first, implement after approval, every diff reviewed before it is accepted.
- Every commit by hand, with my own message — the history is mine.
- A test counts only after I have seen it fail for the right reason: each rule test mutated once.
- Rejected suggestions stay on the record: a brittle selector, a wrong expected status.
- Playwright MCP: the model reads the live DOM before proposing locators; same review rules apply.
- Docs in `documents/` are re-checked after every build step, so the spec never drifts from the code.

## Opening (1 min)
- Java framework: REST Assured for the API, Playwright for the UI, JUnit 5 running in parallel, Allure reporting, SLF4J logging.
- Under test: a courier shift booking system — zones, shift capacity, booking rules, overlap checks.

## Demo (5 min)
1. `docker compose ps` — application healthy.
2. `mvn test -Dgroups=api` — API suite.
3. `mvn test -Dgroups=ui -Dui.headless=false` — one journey, visible browser.
4. `mvn allure:serve` — steps, request/response bodies, screenshot and trace on a failure.
5. `mvn test -Dgroups=bugs -DexcludedGroups=` — 13 known bugs replayed, all red by design (BUG-11…13 from the reschedule PR).
6. README: how to use, requirements map, tooling decisions; `documents/BUGS.md`.

## Why these tools
- Maven — the exercise's own command; the application is Maven too.
- JUnit 5 — Spring Boot ecosystem default; extensions for failure artifacts; parallel execution in a properties file.
- Playwright — auto-wait, trace viewer, bundled browsers; explicit waits kept where the condition carries meaning.
- REST Assured specs — one place for base URL, headers, logging and the response-time ceiling.
- Records + Jackson — immutable DTOs, no annotation processing.
- ThreadLocal browser — Playwright is not thread-safe; one browser per worker, one page per test.
- Framework in src/main — a library; tests under src/test read as business cases.

## How tests stay independent
- Every test creates its own courier and shift through the API; no seed rows, no order dependency; parallel from the first commit.

## Bugs are tests (10 found, all confirmed — `documents/BUGS.md`)
- Sources: the application README is the spec where it states a rule (BUG-01, 02, 03, 06); elsewhere the expectation is written down as an assumption with its reasoning — a product decision to confirm, not hidden.
- Highlight: BUG-01, a race — one courier books overlapping shifts when requests arrive together (5/5 reproduced); root cause and fix in the report.
- One bug is our own finding from the HTTP logs: `createdAt` differs between create response and read (BUG-09).
- Each BUGS.md entry has a `@Tag("bugs")` test asserting the correct behaviour — red by design while the bug exists, green the day it is fixed.
- Excluded by default (pom property), so `mvn clean test` stays green; `mvn test -Dgroups=bugs -DexcludedGroups=` replays every known bug on demand.

## Extending the framework (options)
- New page: extend BasePage, locators by id/role, actions return the next page, no assertions.
- New endpoint: one method in the resource client using Specs.request(); record in api/model if the payload is new.
- New test: extend BaseApiTest / BaseUiTest; data from builders; tag api/ui, plus smoke or bugs.
- Negative case: raw client call, Specs.status(409), assert ApiError.message().
- Data-driven: @ParameterizedTest with @CsvSource over a builder override.

## What I would do next
- CI pipeline, Testcontainers for the application, OpenAPI contract validation, k6 on POST /bookings, event assertions, data-testid request to the developers.

## Working with AI — next step
- Test candidates generated from the OpenAPI document, human-verified before they count.
- Flaky-test triage over Allure history; AI review gate on pull requests.