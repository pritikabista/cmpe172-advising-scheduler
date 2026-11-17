DROP TABLE IF EXISTS appointment_reminders CASCADE;
DROP TABLE IF EXISTS notification_outbox CASCADE;
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

-- Helpful indexes for the queries used by the app
CREATE INDEX idx_slots_start_time        ON availability_slots (start_time);
CREATE INDEX idx_appointments_customer   ON appointments (customer_id);

-- Milestone 3: notifications that could not be delivered (fallback) wait here for the retry job.
-- event_id is the primary key, so the same event can never be queued twice (idempotent).
CREATE TABLE notification_outbox (
    event_id    VARCHAR(100) PRIMARY KEY,
    recipient   VARCHAR(100) NOT NULL,
    type        VARCHAR(30)  NOT NULL,
    message     VARCHAR(500) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts    INT          NOT NULL DEFAULT 0,
    last_error  VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_outbox_pending ON notification_outbox (status, created_at);

-- Milestone 4, Agent 2: one row per appointment that already got its AI reminder
-- (so the autonomous agent never reminds the same appointment twice).
CREATE TABLE appointment_reminders (
    appointment_id  BIGINT       PRIMARY KEY REFERENCES appointments(id) ON DELETE CASCADE,
    message         VARCHAR(500) NOT NULL,
    ai_generated    BOOLEAN      NOT NULL,
    delivered       BOOLEAN      NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Milestone 4 bonus: persistent chat memory for Agent 1 (Spring AI JDBC chat memory, PostgreSQL schema).
-- NOT dropped on restart, so conversations survive an app restart.
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(36) NOT NULL,
    content         TEXT        NOT NULL,
    type            VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp"     TIMESTAMP   NOT NULL,
    sequence_id     BIGINT      NOT NULL
);
CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_SEQUENCE_ID_IDX
    ON SPRING_AI_CHAT_MEMORY (conversation_id, sequence_id);
