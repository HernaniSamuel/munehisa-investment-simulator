-- Seeds a single pre-verified user for the k6 load test suite. There is no API path to reach
-- a verified account (real registration requires clicking an emailed verification link, and
-- adding a test-only bypass endpoint would be an application-code change, out of scope for
-- this issue), so this script inserts the user directly.
--
-- Must stay in sync with LOAD_TEST_USER in ../config/identifiers.js (same email/password).
--
-- pgcrypto's crypt(password, gen_salt('bf')) produces a standard bcrypt ($2a$) hash, which
-- Spring Security's BCryptPasswordEncoder verifies correctly (jBCrypt treats $2a$/$2b$/$2y$
-- interchangeably) -- no external hash precomputation needed.
--
-- Idempotent: safe to re-run (e.g. after `docker compose down -v`). Run once, after `backend`
-- has started at least once so Flyway has created the `users` table. See README.md.
--
-- Usage (from the repo root, with the compose stack up):
--   docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" < src/backend/load-tests/seed/seed-load-test-user.sql

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (id, name, email, password, is_verified)
VALUES (
    gen_random_uuid(),
    'k6 Load Test User',
    'k6-loadtest@munehisa.invalid',
    crypt('Load-Test-P4ssw0rd!', gen_salt('bf')),
    TRUE
)
ON CONFLICT (email) DO NOTHING;
