# TESTS — Courier Shifts (fullstack-booking-system)

Every test in the framework, grouped by class, with what it does and what it proves.
Each test seeds its own couriers, shifts and bookings through the API, so none depends on seed
rows, other tests or execution order.

**In numbers:** 27 test methods, 40 executions (parameterized cases counted separately).
The default run executes 30; the 10 known-bug tests (tag `bugs`) are excluded and expected to fail
until their bug is fixed (see [BUGS.md](BUGS.md)).

**Tags:** `api` / `ui` by layer, `smoke` for the quick health check, `bugs` for known-bug tests.

---

## API tests

### GET /meta — `MetaApiTest` (smoke)

**Returns all zones and vehicle types within the response-time ceiling**
Calls `GET /meta` and compares the returned zones and vehicle types with the framework's `Zone` and
`VehicleType` enums, as sets plus a size check that catches duplicates. If the server adds or renames
a value, this test fails first and points at the enum to update.

### POST /shifts — `CreateShiftApiTest`

Boundary values for the server's shift rules: start in the future, end after start, duration 1–12 h
inclusive, capacity 1–50. Each limit is tested on both sides.

**Accepts values on the inner side of every limit** (4 cases)
Creates shifts of 60 min, 720 min, capacity 1 and capacity 50. Each returns 201 with the sent zone,
start and end echoed back, `bookedCount` 0, `availableSlots` equal to capacity and status `OPEN`.

**Rejects durations on the outer side of the limits** (3 cases)
59 min and 721 min return 400 "Shift duration must be between 1 and 12 hours"; 0 min (end equals
start) returns 400 "Shift end time must be after start time".

**Rejects capacity outside 1–50** (2 cases)
Capacity 0 and 51 return 400 "Validation failed" with `fieldErrors` containing `capacity`. The key is
asserted, not the constraint text, which is server wording rather than contract.

**Rejects a start time in the past**
Returns 400 "Shift start time must be in the future".

### POST /bookings — `CreateBookingApiTest`

**201 CONFIRMED: contract, echoed courier/shift data, and the slot is taken**
Books a capacity-2 shift. The response matches the booking JSON schema, has status `CONFIRMED`, echoes
the courier's id and name and the shift's id, zone, start and end, has `createdAt` set and no
`cancelledAt`. Reading the shift back shows `bookedCount` 1 and one slot left.

**Rejects a booking that breaks a rule (server precedence order)** (8 cases)
One case per server rule, each seeding only what it needs to break its own rule so a failure points at
exactly one rule. Every case checks status, the server's literal message, `path` `/api/bookings`, and
that `fieldErrors` is absent for business rules.
- Unknown courier → 404 "Courier with id … not found"
- Unknown shift → 404 "Shift with id … not found"
- Inactive courier → 409 "Courier is inactive and cannot book shifts"
- Shift already started → 409 "Shift has already started and can no longer be booked"
  (the shift is created 3 s ahead and the test waits until the server reports it `IN_PROGRESS`)
- Same courier books the same shift twice → 409 "Courier has already booked this shift"
- Courier already holds an overlapping shift → 409 "Courier already has a booking that overlaps with this shift"
- Capacity-1 shift already taken → 409 "Shift is full"
- `courierId` and `shiftId` sent as JSON null → 400 "Validation failed" with `fieldErrors` naming both fields

**201 for a back-to-back shift: end == next start is not an overlap**
A courier with a booked shift books a second one that starts exactly when the first ends; it is
`CONFIRMED`.

**201 when re-booking a shift after cancelling: only CONFIRMED bookings block**
A courier books a shift, cancels, and books it again. The new booking is `CONFIRMED` and has a new id,
proving a new row was created rather than the old one revived.

### POST /bookings/{id}/cancel — `CancelBookingApiTest`

Cancelling is a state change, never a delete: the booking row stays.

**200 CANCELLED: same booking, cancelledAt set, contract holds**
Cancels a confirmed booking. The response matches the schema, keeps the same id, courier and shift,
has status `CANCELLED`, and `cancelledAt` is set and not before `createdAt`.

**409 on a second cancel of the same booking**
Cancelling an already cancelled booking returns 409 "Booking is already cancelled".

**Cancelling frees the slot: availableSlots back up by one**
On a capacity-2 shift, `availableSlots` is 1 while booked and 2 after cancelling, with `bookedCount`
back to 0. The "while booked" value is asserted too, so a test that never consumed the slot cannot pass.

### POST /bookings under concurrency — `BookingConcurrencyTest`

Data is seeded first; then all booking requests are released at the same instant, each from its own
thread, behind a start gate so no request gets a head start.

**Capacity-1 shift, 10 couriers at once: exactly one 201, nine 409 "Shift is full"**
Ten different couriers race for a single slot. Exactly one wins; the other nine get 409 "Shift is
full". The shift then shows `bookedCount` 1, `availableSlots` 0 and status `FULL`.

**BUG-01: one courier, 5 overlapping shifts at once: only one booking may succeed** (tag `bugs`, expected to fail)
One courier books five shifts with identical times in parallel. Correct behaviour is one 201, four 409
and a single `CONFIRMED` booking for the courier. Today the overlap check is not serialised, so several
bookings succeed. A lucky run can still pass, which is itself a sign of a race.

### Known bugs (API) — `KnownBugsApiTest` (tag `bugs`, all expected to fail)

Each test asserts the correct behaviour: the application README where it states a rule, otherwise the
expectation assumed and written down in BUGS.md. A test turning green means the bug was fixed.

**BUG-02: a name of 1 character after trimming is rejected (README: 2–100 chars)**
Creates a courier named `"  A  "`. It passes the length check as sent, then the server trims and
stores `"A"`. Expected: 400 with a `name` field error.

**BUG-03: a phone with 1 digit is rejected (README: 7–20 digits)**
Creates a courier with phone `1------`; the server counts dashes toward the length. Expected: 400 with
a `phone` field error.

**BUG-04: an unsupported Content-Type is answered with 415, not 500**
Posts a booking as `text/plain`. Expected: 415 Unsupported Media Type; today the server returns 500.

**BUG-05: deactivating a courier does not leave them holding confirmed future bookings**
Books a future shift, then deactivates the courier. Expected: the booking is no longer `CONFIRMED`.
The assertion accepts either fix (cancel the bookings, or refuse the deactivation).

**BUG-08: an unknown courier is 404 on both booking endpoints**
`GET /couriers/{unknown}/bookings` returns 404, but `GET /bookings?courierId={unknown}` returns 200
with an empty list. Expected: 404 on both.

**BUG-09: createdAt returned on create equals createdAt read back**
Creates a courier, a shift and a booking and reads each back. Expected: `createdAt` is identical.
Today create responses carry nanoseconds, while reads carry what Postgres stored (microseconds).

**BUG-10: an invalid enum in the body is reported as a field error**
Creates a courier with vehicle type `HELICOPTER`. Expected: 400 "Validation failed" with a
`vehicleType` field error.

---

## UI tests

UI tests seed data through the API and drive only the journey itself in the browser. On failure a
screenshot and a Playwright trace are attached to the Allure report.

### Shifts page — `ShiftsSmokeTest` (smoke)

**Opens with the shifts table visible**
The UI is served and renders the shifts table.

**Lists a shift created through the API with its zone, capacity and OPEN badge**
A shift created through the API appears as a row with the right zone, booked cell `0 / 3` and an `OPEN`
status badge. Expected values come from the API response; locale-dependent time cells are not
asserted.

### Book a shift — `BookShiftE2ETest` (smoke)

**Books an open shift: success toast, booked count, stored CONFIRMED, listed in Bookings**
The critical journey. A dispatcher opens Shifts, clicks Book on a capacity-2 shift, picks the courier
and confirms. The UI shows a "Shift booked" toast and `1 / 2`. The API confirms exactly one
`CONFIRMED` booking for that courier and shift, and the Bookings tab lists it with the courier's name
and a `CONFIRMED` badge.

### Book a shift: rejected — `BookShiftNegativeE2ETest`

**Overlapping shift: error toast with the API reason, dialog stays open, nothing stored**
The same journey for a courier who already holds an overlapping shift. The UI shows the API's message
"Courier already has a booking that overlaps with this shift", keeps the dialog open so another courier
can be picked, and the shift still shows `0 / 2`. The API confirms no booking was stored.

### Known bugs (UI) — `KnownBugsUiTest` (tag `bugs`, all expected to fail)

**BUG-06: editing only the capacity of a booked shift succeeds when its start has seconds**
A booked shift whose start time has seconds is edited in the UI, changing only the capacity. Expected:
"Shift updated" and the new capacity in the booked cell. Today the form drops the seconds, the server
sees a time change on a booked shift and answers 409.

**BUG-07: a shift in progress stays visible in the default "Upcoming only" view**
A shift is created 3 s ahead and the test waits until the server reports it `IN_PROGRESS`. Expected: it
is still listed on the Shifts page by default; today it disappears.
