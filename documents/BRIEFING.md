# BRIEFING — application facts, framework spec, build plan

Source of truth for this repository. Facts below were verified in the application's source
(backend controllers, services, DTOs, Flyway migrations, frontend/app.js). When the running
application disagrees with this file, the running application wins — note the difference.

## 1. Application under test

| Item | Value |
|---|---|
| Name | "Courier Shifts" — couriers book shifts in delivery zones |
| Stack | Spring Boot 3.5.6, Java 21, PostgreSQL 16, Flyway (schema + seed), nginx serving a vanilla-JS single page |
| Ports | UI `http://localhost:3000` (nginx, proxies `/api/` to backend) · API `http://localhost:8080/api` · Postgres 5432 |
| Health | `GET http://localhost:8080/actuator/health` → `{"status":"UP"}`. Do NOT use `:3000/actuator/health` — nginx returns `index.html` with 200 for unknown paths. |
| OpenAPI | `http://localhost:8080/v3/api-docs` · Swagger UI `http://localhost:8080/swagger-ui.html` |
| Auth | None. No users, no login, no tokens. CORS open. |
| Time | All timestamps `java.time.Instant`, ISO-8601 UTC with `Z`. Fraction digits vary (`.123Z`, `.123456Z`, none) — parse as `Instant`, never with a fixed pattern. |
| Reset | `docker compose down -v && docker compose up -d` in the application repo. There is no reset endpoint. |

### 1.1 Enums
- `Zone`: `DOWNTOWN`, `NORTH`, `SOUTH`, `EAST`, `WEST`
- `VehicleType`: `BIKE`, `SCOOTER`, `CAR`
- Booking status: `CONFIRMED`, `CANCELLED`
- Shift status (derived, read-only): `OPEN`, `FULL`, `IN_PROGRESS`, `COMPLETED`

### 1.2 Endpoints

| Method · path | Request | Success | Errors |
|---|---|---|---|
| GET `/meta` | — | 200 `{"zones":[5],"vehicleTypes":[3]}` (key order not guaranteed) | — |
| GET `/couriers?active=` | optional bool | 200 `CourierResponse[]` sorted by name | 400 bad param |
| GET `/couriers/{id}` | — | 200 | 404 |
| POST `/couriers` | `CourierRequest` | **201** `CourierResponse` | 400 `fieldErrors`; 409 duplicate email (case-insensitive) |
| PUT `/couriers/{id}` | `CourierRequest` (`active` null ⇒ unchanged) | 200 | 400 / 404 / 409 |
| DELETE `/couriers/{id}` | — | **204** | 404; 409 "Courier has bookings and cannot be deleted. Deactivate it instead" (CANCELLED counts) |
| GET `/couriers/{id}/bookings` | — | 200 `BookingResponse[]` | 404 |
| GET `/shifts?zone=&from=&to=` | ISO instants with `Z`; filter on `startTime`, inclusive | 200 `ShiftResponse[]` sorted by startTime | 400 bad enum/date; 400 "'from' must be before 'to'" |
| GET `/shifts/{id}` | — | 200 | 404 |
| POST `/shifts` | `ShiftRequest` | **201** `ShiftResponse` (bookedCount 0, status OPEN) | 400 `fieldErrors` (capacity 1–50, nulls); 400 message "Shift start time must be in the future" / "Shift end time must be after start time" / "Shift duration must be between 1 and 12 hours" (inclusive) |
| PUT `/shifts/{id}` | `ShiftRequest` | 200 | 404; 409 "Shift has already started and can no longer be modified"; 400 time rules; 409 "Shift times cannot be changed while it has confirmed bookings"; 409 zone change with bookings; 409 "Capacity cannot be lower than the number of confirmed bookings (N)" |
| DELETE `/shifts/{id}` | — | **204** | 404; 409 "Shift has bookings and cannot be deleted" |
| GET `/shifts/{id}/bookings` | — | 200 `BookingResponse[]` (all statuses) | 404 |
| GET `/bookings?courierId=&shiftId=&status=` | optional filters | 200 `BookingResponse[]` sorted createdAt DESC; unknown courierId ⇒ `[]` | 400 bad enum |
| GET `/bookings/{id}` | — | 200 | 404 |
| POST `/bookings` | `{"courierId":Long,"shiftId":Long}` both required | **201** `BookingResponse` status CONFIRMED | see 1.3 |
| POST `/bookings/{id}/cancel` | no body | **200** `BookingResponse` status CANCELLED, `cancelledAt` set | 404; 409 "Booking is already cancelled"; 409 "Shift has already started; booking can no longer be cancelled" |
| other | — | — | 405 `{"message":"Request method 'X' is not supported"}`; unknown path 404 "Resource not found" |

### 1.3 POST /bookings rule precedence (server checks in this order)
1. 404 `Courier with id X not found`
2. 404 `Shift with id X not found`
3. 409 `Courier is inactive and cannot book shifts`
4. 409 `Shift has already started and can no longer be booked` (started = `now >= startTime`; a past shift cannot be created, so tests create one a few seconds ahead and wait for IN_PROGRESS)
5. 409 `Courier has already booked this shift`
6. 409 `Courier already has a booking that overlaps with this shift` — half-open interval: back-to-back shifts (end == start) are allowed; one minute of overlap is rejected
7. 409 `Shift is full`
8. 400 `Validation failed` with `fieldErrors` when `courierId`/`shiftId` is null

Capacity is guarded by a DB row lock on the shift (concurrent bookings on one capacity-1 shift → exactly one 201). The overlap check is NOT serialized per courier (two concurrent bookings by one courier on two overlapping shifts can both succeed) — candidate bug, verify by test.

### 1.4 Error JSON (single shape; `fieldErrors` present only for bean validation)
```json
{"timestamp":"2026-10-28T14:40:12.123456Z","status":409,"error":"Conflict","message":"Shift is full","path":"/api/bookings"}
```
```json
{"timestamp":"...","status":400,"error":"Bad Request","message":"Validation failed","path":"/api/bookings","fieldErrors":{"courierId":"must not be null","shiftId":"must not be null"}}
```
Other 400s carry a plain message: `Malformed request body` (bad JSON, wrong type, invalid enum string in a body) · `Invalid value for parameter 'x'` (bad query/path param). When a field fails two constraints (`name:""` → NotBlank + Size) only one message survives — assert the key exists, not the exact text.

### 1.5 Validation rules
- Courier: `name` NotBlank, size 2–100 (checked before trim); `email` NotBlank, well-formed, max 150, stored lowercase+trimmed, unique; `phone` NotBlank, regex `^\+?[0-9][0-9\- ]{6,19}$` (dashes/spaces count toward length); `vehicleType` NotNull; `active` Boolean (null on POST ⇒ true).
- Shift: `zone` NotNull; `startTime` in the future; `endTime` after start; duration 1–12 h inclusive; `capacity` 1–50.

### 1.6 Wire format examples and matching records
POST `/bookings` request: `{"courierId": 2, "shiftId": 2}`
Response 201 (`cancelledAt` is present as `null`):
```json
{"id":5,"courierId":2,"courierName":"Bob Smith","shiftId":2,"zone":"NORTH","shiftStart":"2026-10-29T04:00:00Z","shiftEnd":"2026-10-29T08:00:00Z","status":"CONFIRMED","createdAt":"2026-10-28T14:40:12.123456Z","cancelledAt":null}
```
```java
record BookingRequest(Long courierId, Long shiftId) {}
record Booking(Long id, Long courierId, String courierName, Long shiftId, String zone,
               Instant shiftStart, Instant shiftEnd, String status, Instant createdAt, Instant cancelledAt) {}
record ShiftRequest(String zone, Instant startTime, Instant endTime, Integer capacity) {}
record Shift(Long id, String zone, Instant startTime, Instant endTime, int capacity,
             long bookedCount, long availableSlots, String status, Instant createdAt) {}
record CourierRequest(String name, String email, String phone, String vehicleType, Boolean active) {}
record Courier(Long id, String name, String email, String phone, String vehicleType, boolean active, Instant createdAt) {}
record ApiError(Instant timestamp, int status, String error, String message, String path, Map<String, String> fieldErrors) {}
```
Annotate response records with `@JsonIgnoreProperties(ignoreUnknown = true)`.

### 1.7 Seed data — read-only, never depend on it
Couriers 1–4: Alice Johnson (BIKE), Bob Smith (SCOOTER), Carla Gomez (CAR), David Lee (BIKE, **inactive**); emails `alice|bob|carla|david@example.com` (re-POST ⇒ 409).
Shifts 1–5 are relative to DB start time and age out (DOWNTOWN cap 3 with 2 bookings; NORTH cap 2; SOUTH cap 5; EAST cap 1 FULL; WEST in the past, COMPLETED).
Bookings can never be deleted; couriers/shifts with any booking (even CANCELLED) cannot be deleted. The DB only grows. Every test creates its own courier (unique email) and shift(s) far in the future.

## 2. UI facts (frontend/index.html + app.js)

Single page, no routing, URL never changes. Three sections toggled by a `.hidden` class.

| Element | Locator | Notes |
|---|---|---|
| Tabs | `#tab-shifts`, `#tab-couriers`, `#tab-bookings` | buttons |
| Toast | `#notification` (`role="alert"`), class `notification success` or `notification error` | one reused element; text replaced; auto-hides after **4000 ms** by adding `.hidden`; never removed from DOM. Assert text immediately after the action with a retrying assertion. |
| Success texts | `Shift booked`, `Shift created`, `Shift updated`, `Shift deleted`, `Courier created`, `Courier updated`, `Courier deleted`, `Booking cancelled` | error toast text = API `message` (+ `: field msg` when fieldErrors) or `Request failed (NNN)` |
| Tables | `#shifts-table`, `#couriers-table`, `#bookings-table` | `tbody.innerHTML` fully replaced on every load — re-locate rows after any action |
| Row | `tr[data-id="<id>"]` | shifts cells: 1 id · 2 zone · 3 start · 4 end · 5 `"booked / cap"` · 6 `span.badge.<STATUS>` · 7 actions |
| Row actions | `tr[data-id=N] button[data-action="book"\|"edit"\|"delete"\|"bookings"\|"cancel"]` | `disabled` when not applicable (Book only when OPEN) |
| Shift filters | `#shift-zone-filter`, `#shift-upcoming-filter` | "Upcoming only" checked by default → `GET /shifts?from=<now>`; far-future test shifts are visible |
| Booking filters | `#booking-courier-filter`, `#booking-status-filter` | |
| Book dialog | `#book-dialog` (native `<dialog>`; open state `#book-dialog[open]`), `#book-courier` (select; option value = courier id, label `Name (VEHICLE)`, only active couriers), `#book-confirm-btn` (text "Book"), `#book-shift-summary`, `#book-shift-bookings li`, close `#book-dialog [data-close]` | stays open on error; closes on success |
| Shift dialog | `#new-shift-btn`, `#shift-dialog`, `#shift-zone`, `#shift-start`, `#shift-end` (`datetime-local`, value like `2030-01-15T09:00`), `#shift-capacity`, `#shift-save-btn`, `#shift-form-title` | avoid in tests; seed shifts via API |
| Courier dialog | `#new-courier-btn`, `#courier-dialog`, `#courier-name`, `#courier-email`, `#courier-phone`, `#courier-vehicle`, `#courier-active`, `#courier-save-btn` | |
| Empty states | `#shifts-empty`, `#couriers-empty`, `#bookings-empty` | |

Rules:
- Native `window.confirm()` on delete shift, delete courier, cancel booking. Playwright auto-dismisses dialogs (= Cancel, no-op). Register `page.onDialog(Dialog::accept)` before clicking.
- After a successful booking the table reload is fire-and-forget: wait for the row cell content, not only for the toast.
- Time strings in tables are locale/timezone dependent (`toLocaleString`) — never assert them; assert ids, zones, counts, badges.
- No `data-testid` anywhere. Strategy: id first, `data-*` second, role/label third, positional `td:nth-child` only for cell asserts.
- `getByRole(BUTTON, name "Book")` needs `setExact(true)` — "Book" is a substring of "Bookings".

Happy-path journey: Shifts tab → row `tr[data-id=<shiftId>]` → `button[data-action="book"]` → `#book-dialog[open]` → select courier by value → `#book-confirm-btn` → toast "Shift booked" → cell 5 becomes `"1 / N"` → Bookings tab shows the row with badge CONFIRMED → `GET /bookings?shiftId=` confirms.

## 3. Framework spec

Stack (pinned in pom.xml): Java 21 target, Maven, JUnit Jupiter 6.1.3 (JUnit 5 API), Playwright Java 1.64.0, REST Assured 6.3.0 + json-schema-validator, Jackson 2.22.4 (databind + jsr310), Allure 3.0.1 (allure-jupiter, allure-rest-assured) + allure-maven 3.1.0, SLF4J 2.0.20 + Logback 1.6.6. No Lombok, no DataFaker, no AssertJ, no AspectJ: assertions with JUnit (`assertEquals`, `assertAll`) and Playwright's `assertThat(locator)`; Allure steps with `Allure.step("...", () -> ...)` lambdas.

Package root `com.booking.tests`.

| Package | Class | Responsibility |
|---|---|---|
| `config` | `Config` (interface), `PropertiesConfig` | keys `ui.base.url`, `api.base.url`, `ui.browser`, `ui.headless`, `api.max.response.ms`; precedence `-D` > env var (`UI_BASE_URL`) > `config/<env>.properties` (`-Denv`) > `config/defaults.properties`; `Config.get()` singleton |
| `api/spec` | `Specs` | `request()` = RequestSpecBuilder: base URI, JSON content type + accept, filters `AllureRestAssured` + `Slf4jLogFilter`; optional bearer from env `API_TOKEN` (seam). `ok()`, `created()`, `status(int)` = ResponseSpecBuilder: status, JSON content type, `expectResponseTime(lessThan(api.max.response.ms), MILLISECONDS)`. One `RestAssuredConfig` (built once): `ObjectMapperType.JACKSON_2` pinned, `jackson2ObjectMapperFactory` → `Json.mapper()`, `LogConfig.blacklistHeader("Authorization")`; attached to every request spec via `setConfig`, never to the global `RestAssured.config` (no shared mutable state under parallel classes) |
| `api/spec` | `Slf4jLogFilter` | logger name `http`; INFO `--> METHOD uri` and `<-- status METHOD uri (ms)` (method repeated so interleaved parallel lines stand alone); DEBUG bodies; never headers |
| `api/client` | `CourierClient`, `ShiftClient`, `BookingClient` | one method per endpoint returning `Response`; setup helpers `createOk(...)` (`Specs.created()`), `ShiftClient.getOk(id)`, `BookingClient.cancelOk(id)` (`Specs.ok()`) returning the record; `Allure.step` inside |
| `api/model` | records from 1.6 + enums `Zone`, `VehicleType` | |
| `data` | `CourierBuilder`, `ShiftBuilder` | `aCourier()` defaults: unique name, email `qa-<uuid8>@example.com`, phone `+1-555-<4 digits>`, random vehicle, active; `.inactive()`, `.vehicle()`, `.name()`, `.phone()`. `aShift()` defaults: random zone, start = `2030-01-01T00:00Z` + own day per call (atomic counter, random seed per JVM) + random hour 00–11, so default shifts never overlap; end = start + 4 h, capacity 2; `.capacity(n)`, `.zone(z)`, `.startingAt(Instant)`, `.startingIn(Duration)` (for "already started"), `.durationHours(h)`, `.duration(Duration)` (minute boundaries), `.overlapping(Shift other)` (1 min overlap), `.backToBack(Shift other)`, `.inThePast()`, `.plusSeconds(s)` (start with seconds; the UI can only hold minutes) |
| `ui/driver` | `PlaywrightManager` | ThreadLocal `Playwright` and `Browser` per thread; `openPage()` creates context (baseURL from config, tracing started with screenshots+snapshots) + page; `page()`, `context()`, `closePage()`, `closeBrowser()` |
| `ui/driver` | `UiTestExtension` | JUnit 5: `BeforeEachCallback` opens page; `AfterTestExecutionCallback` attaches full-page PNG + trace zip (also kept in `target/traces/`) to Allure when the test failed (runs before `@AfterEach`, page still alive; Allure 3 API `Allure.attachment(name, type, stream, AttachmentOptions.withFileExtension(ext))`; evidence errors logged, never thrown); `AfterEachCallback` closes page; `AfterAllCallback` closes browser |
| `ui/pages` | `BasePage` | holds `Page`; `toast()` → `Toast` component |
| `ui/pages` | `ShiftsPage` | `open()` navigates `/` inside `page.waitForResponse(GET /api/shifts)` — no tab click (Shifts is the default section and every tab click re-fetches and re-renders the table); `table()`, `row(id)`, `zoneCell(id)`; `bookShift(id)` (clicks `button[data-action='book']`) → `BookDialog`; `editShift(id)` (clicks `button[data-action='edit']`) → `ShiftDialog`; `bookedCell(id)` locator; `statusBadge(id)` locator; `goToBookings()` |
| `ui/pages` | `BookingsPage` | reached via `NavTabs.bookings()`; `row(bookingId)`, `courierCell(id)`, `statusBadge(id)`. No `cancel`/`filterByCourier` until a test needs them (rows are found by the booking id from the API cross-check) |
| `ui/components` | `BookDialog` | constructor waits for `#book-dialog[open]` (opens only after its two fetches, so open = populated); `selectCourier(courierId)` by option value; `confirm()` wraps the click in `page.waitForResponse` on POST `/api/bookings`, returns `ShiftsPage`; `root()` locator for retrying `isVisible()`/`isHidden()` (no boolean `isOpen()`) |
| `ui/components` | `NavTabs` | `shifts()`, `bookings()`: click inside `waitForResponse` on the tab's list fetch (`/api/shifts?`, `/api/bookings?`), return the page |
| `ui/components` | `ShiftDialog` | constructor waits for `#shift-dialog[open]` (new and edit share it; `datetime-local` inputs hold minutes only); `capacity(n)`; `save()` wraps the click in `waitForResponse` on the PUT/POST to `/api/shifts`, returns `ShiftsPage`; `root()` |
| `ui/components` | `Toast` | locators `message()` = `#notification` (any type: a failed text assertion prints what was actually shown), `success()` = `#notification.success`, `error()` = `#notification.error`; no assertions (CLAUDE.md) — tests use `assertThat(toast.success()).hasText(...)` |
| `util` | `Waits` | `until(BooleanSupplier, Duration timeout, Duration poll, String description)` — fluent poller for non-DOM conditions, inside an Allure step; timeout → `IllegalStateException` |
| `util` | `Json` | shared `ObjectMapper` (`Json.mapper()`) with `JavaTimeModule`, ISO instants, unknown properties ignored, nulls kept |
| test | `BaseApiTest` | `@Tag("api")`; fields `couriers`, `shifts`, `bookings` clients; `BOOKING_SCHEMA` constant |
| test | `BaseUiTest` | `@ExtendWith(UiTestExtension.class) @Tag("ui")`; `page` bound in `@BeforeEach`; same three clients for seeding |

Resources already in place: `config/defaults.properties`, `junit-platform.properties` (classes concurrent, parallelism 2), `allure.properties`, `logback.xml`. Add `src/test/resources/schemas/booking.schema.json` (draft-04; required `id, courierId, shiftId, status, shiftStart, shiftEnd, createdAt`; `status` enum CONFIRMED|CANCELLED).

## 4. Test scope (stop line)

API — `src/test/java/com/booking/tests/api/`
1. `MetaApiTest` — GET /meta 200, 5 zones, 3 vehicle types, under the time ceiling. (first smoke)
2. `CreateBookingApiTest` — 201 + schema + fields; `@ParameterizedTest` rule matrix in precedence order (404 courier, 404 shift, 409 inactive, 409 started, 409 duplicate, 409 overlap, 409 full, 400 nulls); back-to-back allowed; re-book after cancel.
3. `CancelBookingApiTest` — 200 CANCELLED with `cancelledAt`; 409 already cancelled; slot freed (`availableSlots` +1).
4. `CreateShiftApiTest` — `@CsvSource` boundaries: duration 59 m / 60 m / 12 h / 12 h 01 m; capacity 0 / 1 / 50 / 51; end == start; start in the past.
5. `BookingConcurrencyTest` — capacity-1 shift, 10 distinct couriers, 10 parallel POSTs via `ExecutorService` → exactly one 201, nine 409 "Shift is full". Two latches (ready + go) release all requests at once; concurrent responses checked by status/message only (no response-time ceiling under contention). Second case `@Tag("bugs")` BUG-01: one courier, 5 identical-time shifts in parallel → spec: one 201, four 409, one CONFIRMED (5 instead of 2 to make the race reproducible; expected red).

UI — `src/test/java/com/booking/tests/ui/`
6. `ShiftsSmokeTest` — page opens, `#shifts-table` visible; an API-seeded shift shows its row with zone, `"0 / N"` and OPEN badge. (first smoke)
7. `BookShiftE2ETest` — API-seeded courier + shift (cap 2) → UI book → toast "Shift booked" → cell "1 / 2" → API cross-check (exactly one CONFIRMED; gives the booking id) → Bookings tab row for that id: badge CONFIRMED, courier name.
8. `BookShiftNegativeE2ETest` — courier already booked on shift A (via API); UI book overlapping shift B → error toast "Courier already has a booking that overlaps with this shift", dialog still open, B still "0 / 2", API shows no booking on B.

Bugs — every confirmed bug (section 7) gets a covering test tagged `bugs` that asserts the correct (spec) behaviour. **`bugs` tests are expected to FAIL** while the bug exists; green means fixed (or a wrong test). Never weaken an assertion to make one pass. API bugs in `KnownBugsApiTest` (BUG-02, 03, 04, 05, 08, 09, 10), UI bugs in `KnownBugsUiTest` (BUG-06, 07), the concurrent-overlap bug BUG-01 as case 2 of `BookingConcurrencyTest`; DisplayName starts with the BUG id. All 10 confirmed red on 2026-10-28; write-ups in `documents/BUGS.md`. Default run: 30 tests green; bugs run: 10 tests, 10 failures.

Tags: every class `api` or `ui`; `MetaApiTest`, `ShiftsSmokeTest`, `BookShiftE2ETest` also `smoke`; bug tests also `bugs`. The pom property `<excludedGroups>bugs</excludedGroups>` excludes them from a plain `mvn clean test`; `-Dgroups=bugs -DexcludedGroups=` runs only them; `-DexcludedGroups=` runs everything.

Nothing beyond this list without an explicit request. Candidates for "Further work" only.

## 5. Build order
1. `Config` + `PropertiesConfig` → `Json` → `Specs` + `Slf4jLogFilter` → `MetaApiTest` → `mvn clean test` green → `mvn allure:serve` shows the request/response attachment.
2. Records + enums → clients → builders → `CreateBookingApiTest` happy path → mutation check (flip 201→200, see it fail) → rule matrix → `CancelBookingApiTest` → `CreateShiftApiTest`.
3. `PlaywrightManager` → `UiTestExtension` → `BasePage` → `ShiftsPage` → `ShiftsSmokeTest` headed once (`-Dui.headless=false`), then headless.
4. `BookDialog`, `NavTabs`, `Toast`, `BookingsPage` → `BookShiftE2ETest` → `BookShiftNegativeE2ETest`. (PNG + trace in Allure already confirmed by the step 3 forced failure.)
5. `BookingConcurrencyTest`.
6. Bug tests (`@Tag("bugs")`, expected red) → `BUGS.md` entry for each bug confirmed by its failing test (repro, expected vs actual, test name); candidates that do not reproduce are dropped and reported.
7. README: fill "What is covered", "Assumptions and known issues"; report screenshot under `docs/`.

## 6. Working rules (summary of CLAUDE.md)
- Propose a short plan; implement only what is approved; show the diff.
- Never `git add/commit/push`; never create files not asked for.
- No `Thread.sleep`; no assertions in page objects or clients; no seed IDs; no XPath by index; no hardcoded test data in test methods.
- A test is done when it has been seen to fail for the right reason once.
- Keep every file company-free.

## 7. Bug candidates → all confirmed (see `documents/BUGS.md`)
Status 2026-10-28: every candidate reproduced by its `bugs` test. Mapping: candidates 1–7 = BUG-01…07; candidate 8 split into BUG-08 (unknown courier filter) and BUG-10 (invalid enum in body); BUG-09 (`createdAt` precision differs between create response and read) is our own finding from the HTTP logs.
1. Concurrent overlapping bookings by one courier both return 201 (overlap check not serialized).
2. `name` size validated before trim: `"  A  "` accepted, stored as `"A"`.
3. Phone regex counts separators: `"1------"` accepted; README says 7–20 digits.
4. Unsupported `Content-Type` (e.g. `text/plain`) → 500 "Unexpected error" instead of 415.
5. Deactivating a courier leaves future CONFIRMED bookings intact.
6. UI edit drops seconds from `datetime-local`; a shift with bookings can 409 on PUT with no user change.
7. "Upcoming only" filters on start time; IN_PROGRESS shifts disappear from the default view.
8. `GET /bookings?courierId=999` → 200 `[]` while `/couriers/999/bookings` → 404; invalid enum in body → "Malformed request body" without `fieldErrors`, in query → "Invalid value for parameter".