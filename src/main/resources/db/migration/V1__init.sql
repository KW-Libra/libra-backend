-- =====================================================================
-- V1 — initial schema
-- =====================================================================
-- backend (A-2): users 만 소유. 도메인 데이터는 libra-agent 가 소유.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- users ----------------------------------------------------------------
CREATE TABLE users (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           VARCHAR(254) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    display_name    VARCHAR(80),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_users_email ON users (email);

COMMENT ON TABLE  users               IS '인증 사용자';
COMMENT ON COLUMN users.password_hash IS 'bcrypt strength 12';
