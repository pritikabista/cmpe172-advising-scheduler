# SJSU Campus Advising Scheduler – CMPE 172

Online appointment scheduling system where SJSU students book advising
appointments with academic advisors.

**Stack:** Java 21, Spring Boot 4.1, PostgreSQL 16, JDBC (JdbcTemplate, no ORM), Thymeleaf

## Milestone 1 – What's implemented
- Database schema for users, providers, services, availability_slots, appointments
- Double-booking guard (partial unique index on `appointments(slot_id)` where status = 'BOOKED')
- `schema.sql` + `seed.sql` loaded automatically on startup
- Layered architecture: Controller → Service → Repository → DB
- `GET /` – home page listing advising services
- `GET /slots?page=0` – available slots (paginated with SQL LIMIT/OFFSET)

## Prerequisites
- Java 21
- Docker Desktop

## How to run
1. Start PostgreSQL:
```bash
   docker run --name appt-db -e POSTGRES_USER=appt -e POSTGRES_PASSWORD=appt \
     -e POSTGRES_DB=appointments -p 5432:5432 -d postgres:16
```
2. Run the app:
```bash
   ./mvnw spring-boot:run
```
3. Open http://localhost:8080/ and http://localhost:8080/slots

## Configuration
DB settings come from environment variables (local defaults shown):

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/appointments` |
| `DB_USER` | `appt` |
| `DB_PASSWORD` | `appt` |

## Seed accounts
All seed users have the password `password123` (stored BCrypt-hashed).
- Advisors: `advisor_kim`, `advisor_patel`
- Students: `student_alex`, `student_maya`, `student_leo`

## Code walkthrough video
Milestone 1: 