# SJSU Campus Advising Scheduler – CMPE 172

Online appointment scheduling system where SJSU students book advising
appointments with academic advisors.

**Stack:** Java 21, Spring Boot 4.1, Spring Security, PostgreSQL 16, JDBC (JdbcTemplate, no ORM), Thymeleaf

## Milestone 2 – Booking & Concurrency
- **Login + RBAC** – session-based form login (Spring Security), BCrypt passwords, role read from `users.role` via JDBC.
  `/provider/**` = PROVIDER only, `/appointments/**` and `/slots/*/book` = CUSTOMER only; wrong role → 403.
- **Student:** browse / filter (advisor, service, date) / paginate slots (SQL `LIMIT`/`OFFSET`), book with notes,
  confirmation page, My Appointments (upcoming + history), owner-only cancel.
- **Advisor:** create slots (end time = service length, overlap check), delete own unbooked slots, view bookings.
- **Status:** BOOKED / CANCELLED stored; past BOOKED shown as COMPLETED.
- **Concurrency:** `@Transactional(isolation = READ_COMMITTED)` booking with `SELECT … FOR UPDATE` row lock,
  version bump, partial unique index as DB backstop, retry (max 3) on lock timeout/deadlock.
- **Errors:** global handler → 400 / 403 / 404 / 409 / 500 error page, no stack traces sent to the client.
- **Tests:** Mockito unit tests for booking rules, owner-only cancel, validation, retry; DB tests incl. a
  two-thread test asserting exactly one of two simultaneous bookings succeeds.

### Main files
| Layer | Files |
|---|---|
| Security | `config/SecurityConfig.java` |
| Controllers | `SlotController`, `BookingController`, `ProviderController`, `AuthController`, `GlobalExceptionHandler` |
| Services | `BookingService` (validation + retry), `SlotBookingTransaction` (the `@Transactional` booking), `ProviderService`, `SchedulingService` |
| Repositories | `SlotRepository`, `AppointmentRepository`, `UserRepository`, `ProviderRepository`, `ServiceRepository` |
| Tests | `service/*UnitTest.java` (no DB), `BookingServiceTest.java` (Postgres, two-thread test) |

## Milestone 3 – Integration, Distribution & Deployment
- **Mock notification service** (`mocknotify/`): a second controller at `/mock-notify` that stands in for an
  external email/SMS provider. Logs + stores notifications in memory, keyed by `eventId` (duplicates ignored).
  Can simulate failures: `POST /mock-notify/admin/mode?value=DOWN|SLOW|UP`.
- **Distribution boundary** (`notification/`): `NotificationGateway` interface + `HttpNotificationGateway`
  sends one coarse-grained `NotificationRequest` DTO `{eventId, recipient, type, message}` over HTTP/JSON.
  Timeout (500 ms connect / 1000 ms read), retry ×3 with backoff, `Idempotency-Key` header.
- **Commit-then-notify**: `BookingService` commits the booking/cancel first, then calls `AppointmentNotifier`.
  If the notification fails, the booking still succeeds and the notification is saved to `notification_outbox`
  (fallback). `NotificationRetryJob` re-sends PENDING ones every 60 s (recovery).
- **Health**: `GET /health` → DB check (`SELECT 1` + response time) and notification service check.
  `UP` (200), `DEGRADED` (200, notifications down), `DOWN` (503, DB down).
- **Metrics** (Micrometer): `/actuator/metrics/bookings.created`, `bookings.failed` (tag `reason`),
  `bookings.cancelled`, `booking.latency`, `notifications.sent`, `notifications.failed`, `notifications.pending`.
- **Delivery**: `Dockerfile` (multi-stage), `docker-compose.yml` (app + PostgreSQL),
  GitHub Actions `.github/workflows/ci.yml` (build + all tests on every push, then Docker build).
- **Diagram**: `docs/distribution-diagram.png`

### Run the whole system with Docker
```bash
docker stop appt-db           # free port 8080/5432 if your local setup is running
docker compose up --build     # app + database, http://localhost:8080
docker compose down           # stop
```

### Useful URLs
| URL | What |
|---|---|
| http://localhost:8080/health | health check |
| http://localhost:8080/actuator/metrics/bookings.created | a metric |
| http://localhost:8080/mock-notify/api/notifications | notifications the mock received |

### Simulate the notification service being down
```bash
curl -X POST "http://localhost:8080/mock-notify/admin/mode?value=DOWN"
# book a slot in the browser -> booking still succeeds, /health shows DEGRADED
curl -X POST "http://localhost:8080/mock-notify/admin/mode?value=UP"
# within 60 s the retry job delivers the queued notification
```

## Prerequisites
- Java 21
- Docker Desktop

## How to run
1. Start PostgreSQL (local dev; `docker compose` now runs the full app + DB, see above):
```bash
docker start appt-db   # if you created it before, otherwise:
docker run --name appt-db -e POSTGRES_USER=appt -e POSTGRES_PASSWORD=appt -e POSTGRES_DB=appointments -p 5432:5432 -d postgres:16
```
2. Run the app (schema + seed reload on every start):
```bash
./mvnw spring-boot:run
```
3. Open http://localhost:8080/

## Run tests
Postgres must be running (the DB tests use it):
```bash
./mvnw test
# just the two-thread test:
./mvnw test -Dtest=BookingServiceTest#twoStudentsBookSameSlotAtSameTime_onlyOneSucceeds
```

## Configuration
| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/appointments` |
| `DB_USER` | `appt` |
| `DB_PASSWORD` | `appt` |

## Seed accounts
All seed users have the password `password123` (stored BCrypt-hashed).
- Advisors: `advisor_kim`, `advisor_patel`
- Students: `student_alex`, `student_maya`, `student_leo`

## Quick manual checks
- Log in as `student_alex` → `/appointments` shows 1 upcoming, 1 COMPLETED, 1 CANCELLED.
- As a student, open `/provider/slots` → **403**.
- Book a slot in two browsers (normal + incognito, two students) → second one gets **409**.
- `/slots?page=abc` → **400**, `/slots/9999/book` → **404**.

## Videos
- Milestone 1: https://youtu.be/_3F6ODbN2GE
- Milestone 2: https://youtu.be/PJ8vn_LDbqg
- Milestone 3: https://youtu.be/X35z6p8Gvtw 
