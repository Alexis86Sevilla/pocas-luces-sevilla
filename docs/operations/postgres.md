# PostgreSQL: roles, backups, restore

## Least-privilege role

`infra/postgres/app-role.sql` — run as the `postgres` superuser:

```bash
sudo -u postgres psql -f infra/postgres/app-role.sql
```

**Decision:** the script applies the *practical default* — keep the existing
`sevillasinluz_user` as owner of its own `sevillasinluz` database (needed
because Flyway runs DDL migrations at app startup, `flyway.enabled: true`,
`ddl-auto: validate`), but ensures it is `NOSUPERUSER NOCREATEDB NOCREATEROLE
NOREPLICATION` and revokes `PUBLIC`'s default `CONNECT` right on the database.
This requires **no application.yaml or deploy change** and still meaningfully
scopes a compromised app credential to its own database.

A stricter split (separate migration-only role + a CRUD-only
`sevillasinluz_app` role for the running process, using Spring Boot's
`spring.flyway.url/user/password` to point Flyway at a different credential
than `spring.datasource.*`) is documented and commented out in the same file.
It was **not** applied: it needs a new secret (`FLYWAY_DB_PASSWORD`) created
on the VPS before the next deploy, which this agent cannot do remotely. Apply
it later if you want the stronger boundary.

**Verify:**

```bash
sudo -u postgres psql -c "\du sevillasinluz_user"   # Superuser/Create role/Create DB: off
sudo -u postgres psql -c "\l sevillasinluz"          # Owner: sevillasinluz_user
```

**Rollback:** re-grant what was revoked, e.g.
`GRANT CONNECT ON DATABASE sevillasinluz TO PUBLIC;` and
`ALTER ROLE sevillasinluz_user SUPERUSER;` (not recommended).

## Automated backups

`infra/postgres/backup-postgres.sh` + `sevillasinluz-backup.service` + `.timer`.

```bash
sudo install -m 0750 -o postgres -g postgres infra/postgres/backup-postgres.sh /usr/local/sbin/backup-postgres.sh
sudo mkdir -p /var/backups/sevillasinluz && sudo chown postgres:postgres /var/backups/sevillasinluz
sudo cp infra/postgres/sevillasinluz-backup.service /etc/systemd/system/
sudo cp infra/postgres/sevillasinluz-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now sevillasinluz-backup.timer
```

Runs daily at 03:30 Europe/Madrid (`Persistent=true`: a missed run, e.g. VPS
was off, executes on next boot). Dumps go to `/var/backups/sevillasinluz/
sevillasinluz-<timestamp>.dump` (custom `pg_dump -Fc` format), retained 14
days (`find -mtime +14 -delete`), `umask 077` so files are not
world-readable, exits non-zero on any `pg_dump` failure.

**Verify:**

```bash
sudo systemctl list-timers sevillasinluz-backup.timer
sudo -u postgres /usr/local/sbin/backup-postgres.sh   # run once manually
ls -la /var/backups/sevillasinluz
sudo systemctl status sevillasinluz-backup.service     # check exit code after a timer run
```

**Rollback:**

```bash
sudo systemctl disable --now sevillasinluz-backup.timer
sudo rm /etc/systemd/system/sevillasinluz-backup.{service,timer}
sudo systemctl daemon-reload
```

### Strongly recommended: copy backups off the VPS

A backup that lives only on the VPS it protects does not survive disk
failure, provider incident, or account compromise. Documented only (no
credentials to set up remotely from here) — pick one:

```bash
# rclone to any remote (S3, Backblaze B2, another VPS, etc.)
rclone copy /var/backups/sevillasinluz remote:sevillasinluz-backups

# or plain scp to a second machine you control
scp /var/backups/sevillasinluz/*.dump user@other-host:/backups/sevillasinluz/
```

Add this as a second `ExecStart=` line in a copy of the service unit, or as a
separate timer, once you've chosen a destination and configured credentials.

## Restore

**Always restore into a scratch database first**, never directly over
`sevillasinluz`:

```bash
sudo -u postgres createdb sevillasinluz_restore_test
sudo -u postgres pg_restore --clean --if-exists \
  --dbname=sevillasinluz_restore_test \
  /var/backups/sevillasinluz/sevillasinluz-<timestamp>.dump
```

Inspect the restored data, then either point the app at
`sevillasinluz_restore_test` temporarily, or perform the real restore during a
maintenance window:

```bash
sudo systemctl stop sevillasinluz     # stop the app so nothing writes during restore
sudo -u postgres pg_restore --clean --if-exists \
  --dbname=sevillasinluz \
  /var/backups/sevillasinluz/sevillasinluz-<timestamp>.dump
sudo systemctl start sevillasinluz
```

**Verify:** app starts cleanly (`journalctl -u sevillasinluz -n 50`), Flyway
reports no pending/failed migrations, spot-check row counts against the
backup's expected timeframe.

**Rollback:** if the restore looks wrong, restore the *previous* good dump the
same way — `pg_restore --clean` drops existing objects first, so each restore
attempt starts from a clean slate.
