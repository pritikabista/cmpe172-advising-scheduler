DROP TABLE IF EXISTS appointments CASCADE;
DROP TABLE IF EXISTS availability_slots CASCADE;
DROP TABLE IF EXISTS services CASCADE;
DROP TABLE IF EXISTS providers CASCADE;
DROP TABLE IF EXISTS users CASCADE;

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(100) NOT NULL UNIQUE,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'PROVIDER')),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE providers (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(100) NOT NULL,
    department      VARCHAR(100) NOT NULL,
    office_location VARCHAR(100)
);

CREATE TABLE services (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100) NOT NULL UNIQUE,
    description      VARCHAR(255),
    duration_minutes INT NOT NULL CHECK (duration_minutes > 0),
    price            NUMERIC(8,2) NOT NULL DEFAULT 0.00 CHECK (price >= 0)
);

CREATE TABLE availability_slots (
    id          BIGSERIAL PRIMARY KEY,
    provider_id BIGINT    NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    service_id  BIGINT    NOT NULL REFERENCES services(id),
    start_time  TIMESTAMP NOT NULL,
    end_time    TIMESTAMP NOT NULL,
    version     INT       NOT NULL DEFAULT 0,
    CHECK (end_time > start_time),
    CONSTRAINT uq_provider_start UNIQUE (provider_id, start_time)
);

CREATE TABLE appointments (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT      NOT NULL REFERENCES users(id),
    slot_id      BIGINT      NOT NULL REFERENCES availability_slots(id),
    service_id   BIGINT      NOT NULL REFERENCES services(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'BOOKED'
                 CHECK (status IN ('BOOKED', 'CANCELLED')),
    notes        VARCHAR(255),
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP
);

-- DOUBLE-BOOKING GUARD: only one BOOKED appointment per slot
CREATE UNIQUE INDEX uq_one_active_booking_per_slot
    ON appointments (slot_id)
    WHERE status = 'BOOKED';