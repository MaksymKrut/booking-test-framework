# BUGS — Courier Shifts (fullstack-booking-system)

Bugs and inconsistencies found while building this suite, written up as bug reports.

- **Every entry is covered by an automated test tagged `bugs`.** The test asserts the *correct* behaviour, so it is
  **red while the bug exists** and turns green when it is fixed. Red is the expected result — never weaken an
  assertion to make one pass.
- Bug tests are excluded from the default run (`pom.xml` property `excludedGroups=bugs`). Replay all of them:
  `mvn clean test -Dgroups=bugs -DexcludedGroups=` → 13 tests, 13 failures, each with the message quoted below
  (BUG-01 and BUG-12 are races: a lucky run can pass, which is itself a sign of the race).
- **Basis** says where the expected behaviour comes from: **README** = the application README states the rule;
  **PR** = the contract in the description of application PR #1 (booking reschedule);
  **Assumed** = no written rule, the expectation is ours and the reasoning is given (a product decision to confirm).
- Environment: BUG-01…10 on application commit `1ba4281` via `docker compose up -d`, confirmed 2026-10-28.
  BUG-11…13 on PR #1, branch `feature/booking-reschedule`, commit `8a5ab94`, reproduced with curl 2026-10-29.

| ID | Title | Area | Severity | Basis | Test |
|---|---|---|---|---|---|
| [BUG-01](#bug-01) | Parallel requests let one courier book overlapping shifts | API · bookings | High | README | `BookingConcurrencyTest.overlapCheckHoldsUnderParallelBookings` |
| [BUG-02](#bug-02) | Courier name of 1 character accepted (length checked before trim) | API · couriers | Low | README | `KnownBugsApiTest.rejectsNameShorterThanTwoCharactersAfterTrim` |
| [BUG-03](#bug-03) | Phone with 1 digit accepted (separators count as digits) | API · couriers | Medium | README | `KnownBugsApiTest.rejectsPhoneWithOneDigit` |
| [BUG-04](#bug-04) | Unsupported Content-Type returns 500 instead of 415 | API · errors | Low | Assumed | `KnownBugsApiTest.answersUnsupportedContentTypeWith415` |
| [BUG-05](#bug-05) | Deactivated courier keeps confirmed future bookings | API · couriers | Medium | Assumed | `KnownBugsApiTest.deactivationReleasesFutureBookings` |
| [BUG-06](#bug-06) | UI edit of a booked shift fails with 409 although only capacity changed | UI · shifts | Medium | README | `KnownBugsUiTest.capacityOnlyEditKeepsShiftTimes` |
| [BUG-07](#bug-07) | Shifts in progress disappear from the default Shifts view | UI · shifts | Medium | Assumed | `KnownBugsUiTest.inProgressShiftIsListedByDefault` |
| [BUG-08](#bug-08) | Unknown courier: 404 on one bookings endpoint, 200 `[]` on the other | API · bookings | Low | Assumed | `KnownBugsApiTest.unknownCourierIsNotFoundOnBothEndpoints` |
| [BUG-09](#bug-09) | `createdAt` in create responses differs from the stored value | API · all resources | Low | Assumed | `KnownBugsApiTest.createdAtIsStableBetweenCreateAndRead` |
| [BUG-10](#bug-10) | Invalid enum in a request body is not reported as a field error | API · errors | Low | Assumed | `KnownBugsApiTest.invalidVehicleTypeIsReportedAsFieldError` |
| [BUG-11](#bug-11) | Reschedule rejected as "overlap" when the target overlaps only the booking being moved | API · reschedule | Medium | PR | `KnownBugsApiTest.rescheduleExcludesTheMovedBookingFromOverlapCheck` |
| [BUG-12](#bug-12) | Parallel reschedules let one courier end up with overlapping bookings | API · reschedule | High | PR | `BookingConcurrencyTest.overlapCheckHoldsUnderParallelReschedules` |
| [BUG-13](#bug-13) | API reschedules a booking whose shift has already started | API · reschedule | Medium | Assumed | `KnownBugsApiTest.bookingOnStartedShiftCannotBeRescheduled` |

---

<a id="bug-01"></a>
## BUG-01 — Parallel requests let one courier book overlapping shifts
**Severity:** High · **Area:** API `POST /bookings` · **Basis:** README, booking rule 4 ("no confirmed booking that overlaps in time"); the README promises concurrency safety only for capacity.

**Steps**
1. Create a courier and two shifts with the same start and end.
2. Send `POST /bookings` for both shifts **at the same time**:
   ```bash
   B=http://localhost:8080/api/bookings; H='Content-Type: application/json'
   curl -s -X POST $B -H "$H" -d '{"courierId":C,"shiftId":S1}' &
   curl -s -X POST $B -H "$H" -d '{"courierId":C,"shiftId":S2}' & wait
   ```

**Expected:** exactly one `201`; the other `409 "Courier already has a booking that overlaps with this shift"`.
**Actual:** every request returns `201`. The test (5 identical shifts, 5 parallel requests) reproduced it 5 of 5:
`201 responses expected <1> but was <5>` · `409 responses expected <4> but was <0>` · `CONFIRMED bookings of the courier expected <1> but was <5>`.
Sequential requests are rejected correctly (covered by `CreateBookingApiTest`), so the defect is a race.

**Root cause:** `BookingService.java:64` locks only the *shift* row (`findByIdForUpdate`, `PESSIMISTIC_WRITE`), which serialises capacity per shift. The overlap check at `BookingService.java:76` is a plain read per *courier*; requests for different shifts hold different locks and all pass it before any of them commits.
**Suggested fix:** also serialise per courier (lock the courier row `FOR UPDATE` before the overlap check), or enforce it in the database (exclusion constraint on courier + time range for CONFIRMED bookings).

<a id="bug-02"></a>
## BUG-02 — Courier name of 1 character accepted
**Severity:** Low · **Area:** API `POST/PUT /couriers` · **Basis:** README "name required, 2–100 chars".

**Steps:** `curl -s -X POST http://localhost:8080/api/couriers -H 'Content-Type: application/json' -d '{"name":"  A  ","email":"<unique>@example.com","phone":"+1-555-0102","vehicleType":"BIKE"}'`
**Expected:** `400 "Validation failed"`, `fieldErrors.name`.
**Actual:** `201 Created`; per the source the stored name is `"A"` (trimmed after validation). Test: `Expected status code <400> but was <201>`.
**Root cause:** `@Size(min = 2)` (`CourierRequest.java:11`) validates the raw value; `CourierService.java:80` trims afterwards, so the stored value never passed validation.
**Suggested fix:** trim before validating (e.g. in the request record's compact constructor) or validate the trimmed value.

<a id="bug-03"></a>
## BUG-03 — Phone with 1 digit accepted
**Severity:** Medium (unusable contact data) · **Area:** API `POST/PUT /couriers` · **Basis:** README "phone required, 7–20 digits; may start with + and contain spaces or dashes".

**Steps:** as BUG-02 with `"name":"Bug Three","phone":"1------"`.
**Expected:** `400`, `fieldErrors.phone`.
**Actual:** `201 Created`. Test: `Expected status code <400> but was <201>`.
**Root cause:** the regex `^\+?[0-9][0-9\- ]{6,19}$` (`CourierRequest.java:13`) counts dashes and spaces toward the 7–20 length; one digit plus six dashes matches.
**Suggested fix:** count digits only (strip separators, then check `\d{7,20}`), or a lookahead such as `^(?=(?:\D*\d){7,20}\D*$)\+?[0-9][0-9\- ]*$`.

<a id="bug-04"></a>
## BUG-04 — Unsupported Content-Type returns 500 instead of 415
**Severity:** Low (client error reported as server failure; logged as an error) · **Area:** API, all write endpoints · **Basis:** Assumed — HTTP semantics: an unsupported media type is the client's fault, `415 Unsupported Media Type`. The README lists 500 only for "Unexpected error".

**Steps:** `curl -s -X POST http://localhost:8080/api/bookings -H 'Content-Type: text/plain' -d 'courierId=1&shiftId=1'`
**Expected:** `415` (in the standard error format).
**Actual:** `500 {"message":"Unexpected error"}`; per the source the backend also logs it as an error with a stack trace. Test: `Expected status code <415> but was <500>`.
**Root cause:** `GlobalExceptionHandler` has no handler for `HttpMediaTypeNotSupportedException`; it falls into the catch-all `@ExceptionHandler(Exception.class)` (`GlobalExceptionHandler.java:78`).
**Suggested fix:** add a handler mapping `HttpMediaTypeNotSupportedException` to 415 (and `HttpMediaTypeNotAcceptableException` to 406).

<a id="bug-05"></a>
## BUG-05 — Deactivated courier keeps confirmed future bookings
**Severity:** Medium (slots stay blocked by a courier who may not work; no-shows) · **Area:** API `PUT /couriers/{id}` · **Basis:** Assumed — the README forbids inactive couriers from booking; an inactive courier holding future shifts contradicts that intent. Either outcome is acceptable: cancel the future bookings, or refuse deactivation (409) while they exist.

**Steps:** create courier → book a future shift → `PUT /couriers/{id}` with the same data and `"active": false` → `GET /bookings/{bookingId}`.
**Expected:** the booking is no longer CONFIRMED (or the PUT is rejected).
**Actual:** PUT `200`, booking still `CONFIRMED`, slot still taken. Test: `inactive courier still holds a confirmed future booking ==> expected: not equal but was: <CONFIRMED>`.
**Root cause:** `CourierService.update` (`CourierService.java:53–61`) only flips the flag; bookings are never touched.
**Suggested fix:** product decision first; then cancel future CONFIRMED bookings in the same transaction, or return 409 listing them.

<a id="bug-06"></a>
## BUG-06 — UI edit of a booked shift fails with 409 although only capacity changed
**Severity:** Medium (a booked shift cannot be edited from the UI at all) · **Area:** UI Shifts → Edit · **Basis:** README "while a shift has confirmed bookings its times and zone cannot change" — the user did not change them.

**Steps**
1. Via API, create a shift whose start has seconds (e.g. `…T09:00:30Z`; the API accepts any second) and book it.
2. UI: Shifts → Edit on that row → change only Capacity (2 → 3) → Save.

**Expected:** toast "Shift updated", row shows `1 / 3`.
**Actual:** error toast "Shift times cannot be changed while it has confirmed bookings"; capacity unchanged. Test: `Expected: Shift updated · Received: Shift times cannot be changed while it has confirmed bookings`.
**Root cause:** the edit form fills `<input type="datetime-local">` via `toLocalInput` (`app.js:35–39`), which formats minutes only; Save sends `new Date(value).toISOString()` (`app.js:129–130`), i.e. `…T09:00:00Z`. The server compares exact instants (`ShiftService.java:87`) and sees a time change.
**Suggested fix:** keep seconds in the form (`step="1"` and a seconds-aware formatter) or send times only when the user changed them; alternatively the API could reject seconds on create so both sides agree.

<a id="bug-07"></a>
## BUG-07 — Shifts in progress disappear from the default Shifts view
**Severity:** Medium (dispatchers cannot see what is running now) · **Area:** UI Shifts, "Upcoming only" filter (on by default) · **Basis:** Assumed — a shift that has started but not ended is still operationally relevant; "upcoming" should mean "not finished".

**Steps:** via API create a shift starting a few seconds from now; wait until `GET /shifts/{id}` reports `IN_PROGRESS`; open the UI.
**Expected:** the shift is listed (status badge IN_PROGRESS).
**Actual:** not listed in the default view. Test: `Locator expected to be visible · element(s) not found`.
**Root cause:** the filter sends `from=<now>` (`app.js:81`); the API filters on `startTime >= from`, so any started shift is excluded.
**Suggested fix:** filter on end time (`endTime > now`), e.g. a dedicated `active=true` / `endAfter=` parameter.

<a id="bug-08"></a>
## BUG-08 — Unknown courier: 404 on one bookings endpoint, 200 `[]` on the other
**Severity:** Low (inconsistent contract; a typo in an id looks like "no bookings") · **Area:** API bookings · **Basis:** Assumed — the same question should get the same answer.

**Steps:** `curl -i http://localhost:8080/api/couriers/999999/bookings` and `curl -i "http://localhost:8080/api/bookings?courierId=999999"`
**Expected:** both `404 "Courier with id 999999 not found"`.
**Actual:** the first `404`, the second `200 []`. Test: `GET /bookings?courierId={unknown} ==> expected: <404> but was: <200>`.
**Root cause:** `BookingService.list` (`BookingService.java:39–42`) adds the id as a plain filter without checking that the courier exists.
**Suggested fix:** resolve the courier (404 if absent) when `courierId` is given — same for `shiftId`.

<a id="bug-09"></a>
## BUG-09 — `createdAt` in create responses differs from the stored value
**Severity:** Low (clients comparing, caching or de-duplicating by timestamp see two values for one record) · **Area:** API, couriers / shifts / bookings · **Basis:** Assumed — a field returned on create should equal the same field read back.

**Steps:** `POST /shifts` → note `createdAt` → `GET /shifts/{id}`.
**Expected:** identical `createdAt`.
**Actual:** nanoseconds on create, microseconds (rounded) on read. Test:
`courier createdAt expected <…13.422641219Z> but was <…13.422641Z>` · `shift … <…13.433687469Z> but was <…13.433687Z>` · `booking … <…13.447123219Z> but was <…13.447123Z>`.
**Root cause:** `@PrePersist` sets `Instant.now()` (nanosecond precision) in `Courier.java:37`, `Shift.java:34`, `Booking.java:45`; the response is built from that in-memory entity, while PostgreSQL `timestamp` stores microseconds.
**Suggested fix:** truncate in `@PrePersist` (`Instant.now().truncatedTo(ChronoUnit.MICROS)`), or build the response after a flush/refresh.

<a id="bug-10"></a>
## BUG-10 — Invalid enum in a request body is not reported as a field error
**Severity:** Low (client cannot tell which field is wrong) · **Area:** API errors · **Basis:** Assumed — consistency with the documented error format: a wrong *value* in a known field is a validation failure, like a null in that field.

**Steps:** `POST /couriers` with a valid body except `"vehicleType":"HELICOPTER"`.
**Expected:** `400 "Validation failed"`, `fieldErrors.vehicleType`.
**Actual:** `400 "Malformed request body"`, no `fieldErrors` (the same message as broken JSON). In a query parameter the same mistake says `Invalid value for parameter 'x'`. Test: `message expected <Validation failed> but was <Malformed request body>` · `fieldErrors expected: not <null>`.
**Root cause:** Jackson fails to deserialise the enum before bean validation runs; `GlobalExceptionHandler.java:58–60` maps every `HttpMessageNotReadableException` to one generic message.
**Suggested fix:** inspect the cause (`InvalidFormatException` → field path) and return it in `fieldErrors`.

<a id="bug-11"></a>
## BUG-11 — Reschedule rejected as "overlap" when the target overlaps only the booking being moved
**Severity:** Medium (the common "move it an hour later" case fails; the workaround cancel + rebook loses the booking's identity, which is the point of the feature) · **Area:** API `POST /bookings/{id}/reschedule` · **Basis:** PR #1 business rules and README: "The booking being moved is **excluded** from the overlap check".

**Steps**
1. Create a courier, a shift `09:00–13:00` and book it.
2. Create a shift `10:00–14:00` (overlaps only the booked one) and reschedule the booking to it:
   ```bash
   curl -s -X POST http://localhost:8080/api/bookings/B/reschedule -H 'Content-Type: application/json' -d '{"shiftId":T}'
   ```

**Expected:** `200`, the booking now on the target shift.
**Actual:** `409 "Courier already has a booking that overlaps with this shift"`. Reproduced with curl on 2026-10-29.
**Root cause:** `BookingService.java:110` calls the same `courierHasOverlappingBooking(courierId, start, end)` as booking creation; the query (`BookingRepository.java:31–37`) counts every CONFIRMED booking of the courier, including the one being moved.
**Suggested fix:** add the booking id to the query (`and b.id <> :excludeBookingId`) for the reschedule path.

<a id="bug-12"></a>
## BUG-12 — Parallel reschedules let one courier end up with overlapping bookings
**Severity:** High (same consequence as BUG-01: a courier double-booked in time) · **Area:** API `POST /bookings/{id}/reschedule` · **Basis:** PR #1 business rules ("no other confirmed booking that overlaps the target shift"); the PR's locking comment promises that "concurrent reschedules cannot race".

**Steps**
1. Create a courier with several bookings on shifts on different days.
2. Create the same number of target shifts with identical times; send one reschedule per booking to its own target **at the same time**.

**Expected:** exactly one `200`, every other `409 "Courier already has a booking that overlaps with this shift"`.
**Actual:** every reschedule returns `200`; the courier ends with all bookings on overlapping shifts. Reproduced with curl on 2026-10-29 (4 of 4 parallel moves succeeded).
**Root cause:** same as BUG-01. `reschedule` locks the booking row (`BookingService.java:92`) and the target shift row (`:94`) but not the courier, so moves of *different* bookings of one courier hold different locks and all pass the overlap read (`:110`) before any of them commits.
**Suggested fix:** the BUG-01 fix covers both paths: lock the courier row before the overlap check, or a database exclusion constraint on courier + time range for CONFIRMED bookings.

<a id="bug-13"></a>
## BUG-13 — API reschedules a booking whose shift has already started
**Severity:** Medium (a booking of a running or finished shift can be moved away, rewriting who worked when; API and UI disagree) · **Area:** API `POST /bookings/{id}/reschedule` · **Basis:** Assumed — the PR's UI contract enables Reschedule only when the booking's shift starts in the future, and cancel refuses a started shift ("Shift has already started; booking can no longer be cancelled"); the API should enforce what the UI promises.

**Steps:** via API create a shift starting a few seconds from now and book it; wait until `GET /shifts/{id}` reports `IN_PROGRESS`; reschedule the booking to any future shift.
**Expected:** `409` (message to be defined; the PR has none for this case).
**Actual:** `200`, the booking moved off the in-progress shift. Reproduced with curl on 2026-10-29.
**Root cause:** `reschedule` checks only that the *target* has not started (`BookingService.java:106`); there is no check on the booking's current shift, unlike `cancel` (`:131`). The UI check (`app.js:296`) is client-side only.
**Suggested fix:** reject when `booking.getShift().hasStarted(now)`, with its own message, and add it to the PR's error table.

---

## Considered, not filed
- All candidates reproduced; none were dropped.
- Reschedule onto a shift the courier already holds in another booking answers "Courier already has a booking that overlaps with this shift", not "already booked this shift". Matches the PR's error table, so not a defect.
- Reschedule picker (PR #1): an option clicked within the 250 ms search debounce is selected, but the pending filter then runs on the selected label and re-opens the list with "No shifts match" (`app.js:460`; `selectShiftOption`, `app.js:427`, does not clear the timer). Cosmetic, needs a sub-250 ms click; `RescheduleDialog.search` waits for the filter so tests are deterministic.
- `GET :3000/actuator/health` returns `200` with `index.html` (noted in the briefing, not covered by a test): nginx SPA fallback for unknown paths, not an API defect — use `:8080/actuator/health`.
