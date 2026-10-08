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

## Prerequisites
- Java 21
- Docker Desktop

## How to run
1. Start PostgreSQL:
```bash
docker compose up -d
# or: docker run --name appt-db -e POSTGRES_USER=appt -e POSTGRES_PASSWORD=appt -e POSTGRES_DB=appointments -p 5432:5432 -d postgres:16
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
- Milestone 2:
