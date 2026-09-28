-- Least-privilege PostgreSQL role(s) for sevillasinluz.
--
-- === Trade-off: Flyway needs DDL, the running app does not ===============
-- The prod profile (backend/src/main/resources/application.yaml) runs Flyway
-- migrations at application startup with `ddl-auto: validate` (Hibernate never
-- issues DDL) and `flyway.enabled: true`. Flyway migrations DO need DDL rights
-- (CREATE TABLE/ALTER TABLE/etc.), but the running application only ever needs
-- SELECT/INSERT/UPDATE/DELETE on the existing tables.
--
-- Two options:
--
--   (A) SAFE PRACTICAL DEFAULT (recommended here, no code/config change needed):
--       keep ONE role (the existing `sevillasinluz_user`) that OWNS the
--       `sevillasinluz` database and its `public` schema, used for both
--       migrations and runtime queries. It must NOT be a superuser and must
--       have NO privileges on any other database, role, or the instance itself.
--       This is a real improvement over an unconstrained/superuser account
--       with zero deploy risk: a compromise of the app is scoped to its own
--       database, not the whole PostgreSQL instance.
--
--   (B) STRICTER SPLIT (optional future hardening, requires a config change):
--       a separate MIGRATION role (DDL rights, used only by Flyway) and a
--       separate least-privilege APP role (CRUD only, used by the running
--       Spring Boot process). Spring Boot supports this natively via distinct
--       `spring.flyway.url/user/password` properties (falls back to
--       `spring.datasource.*` if unset), so it needs no custom code — only two
--       new secrets/env vars (a migration DB user + password) wired into the
--       prod profile and the deploy pipeline. This was NOT applied in this
--       change: application.yaml and the deploy workflow are left untouched,
--       since it introduces a new credential that must exist on the VPS
--       before the next deploy, and this agent cannot create it there.
--
-- This file sets up (A) as the default, and includes commented statements for
-- (B) if you later decide to do the stricter split.
--
-- Usage (as the postgres superuser, on the VPS):
--   sudo -u postgres psql -f infra/postgres/app-role.sql
--
-- Verify:
--   sudo -u postgres psql -c "\du sevillasinluz_user"
--   -- Superuser / Create role / Create DB should all be OFF for sevillasinluz_user
--   sudo -u postgres psql -c "\l sevillasinluz"
--   -- Owner should be sevillasinluz_user
--
-- Rollback:
--   This script only tightens privileges; it does not drop the role or data.
--   To undo a specific GRANT, use the matching REVOKE statement below.

\set ON_ERROR_STOP on

-- (A) Practical default: ensure the existing app/migration role is not a
-- superuser and cannot create other roles or databases. Run this even if the
-- role already exists; ALTER ROLE is idempotent.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sevillasinluz_user') THEN
    ALTER ROLE sevillasinluz_user NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
  ELSE
    RAISE NOTICE 'Role sevillasinluz_user does not exist yet; create it with CREATE ROLE sevillasinluz_user LOGIN PASSWORD ''...'' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION; first.';
  END IF;
END
$$;

-- Confirm ownership of its own database (adjust if a different role currently
-- owns it) and revoke PUBLIC's default CONNECT right so only this role (and
-- superusers) can connect.
-- ALTER DATABASE sevillasinluz OWNER TO sevillasinluz_user;
REVOKE CONNECT ON DATABASE sevillasinluz FROM PUBLIC;
GRANT CONNECT ON DATABASE sevillasinluz TO sevillasinluz_user;

-- -----------------------------------------------------------------------
-- (B) OPTIONAL stricter split — least-privilege CRUD-only role for the
-- running application, separate from the migration/owner role above.
-- Uncomment and adapt if/when you wire up separate spring.flyway.* credentials.
-- -----------------------------------------------------------------------

-- CREATE ROLE sevillasinluz_app LOGIN PASSWORD '...' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
--
-- REVOKE CONNECT ON DATABASE sevillasinluz FROM PUBLIC;
-- GRANT CONNECT ON DATABASE sevillasinluz TO sevillasinluz_app;
--
-- \c sevillasinluz
--
-- GRANT USAGE ON SCHEMA public TO sevillasinluz_app;
--
-- -- Existing tables/sequences (run once, after migrations have created them).
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO sevillasinluz_app;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO sevillasinluz_app;
--
-- -- Future tables/sequences created by the migration/owner role (Flyway),
-- -- so sevillasinluz_app automatically gets the same rights on new objects
-- -- without re-running this script after every migration.
-- ALTER DEFAULT PRIVILEGES FOR ROLE sevillasinluz_user IN SCHEMA public
--   GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sevillasinluz_app;
-- ALTER DEFAULT PRIVILEGES FOR ROLE sevillasinluz_user IN SCHEMA public
--   GRANT USAGE, SELECT ON SEQUENCES TO sevillasinluz_app;
--
-- -- sevillasinluz_app must NEVER get CREATE on the schema/database (no DDL).
-- REVOKE CREATE ON SCHEMA public FROM sevillasinluz_app;
-- REVOKE ALL ON DATABASE sevillasinluz FROM sevillasinluz_app;
-- GRANT CONNECT ON DATABASE sevillasinluz TO sevillasinluz_app;
--
-- -- Then set, as a NEW secret distinct from DB_PASSWORD (do not reuse it):
-- --   spring.flyway.url=jdbc:postgresql://localhost:5432/sevillasinluz
-- --   spring.flyway.user=sevillasinluz_user
-- --   spring.flyway.password=${FLYWAY_DB_PASSWORD}
-- --   spring.datasource.username=sevillasinluz_app
-- --   spring.datasource.password=${DB_PASSWORD}
-- -- in application.yaml's prod profile, and add FLYWAY_DB_PASSWORD next to
-- -- DB_PASSWORD wherever prod env vars are provisioned.
