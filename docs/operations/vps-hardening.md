# VPS hardening runbook

Target: the Ubuntu VPS running nginx, the `sevillasinluz` systemd service (Spring
Boot, port 8081) and PostgreSQL 16 (port 5432).

**Before starting: keep your current SSH session open.** Every step below that
touches SSH or the firewall includes a lock-out warning. Open a **second**
terminal/SSH session to test each change before closing the first one.

## 1. Firewall (ufw)

```bash
sudo apt-get update && sudo apt-get install -y ufw
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
```

> ⚠️ **Lock-out risk.** `ufw enable` applies the deny-by-default policy
> immediately. If `OpenSSH` was not allowed first, you will lose your SSH
> session and may need out-of-band console access from your VPS provider to
> recover. Keep your current session open and verify `sudo ufw status` lists
> `OpenSSH` (or `22/tcp`) as `ALLOW` before enabling.

```bash
sudo ufw status verbose   # confirm OpenSSH, 80, 443 are ALLOW before enabling
sudo ufw enable
```

**Verify (from a second session, and from an external machine):**

```bash
sudo ufw status numbered
sudo ss -tlnp                 # 8081 and 5432 must NOT show 0.0.0.0/:: — only 127.0.0.1
# From a machine OUTSIDE the VPS:
nc -zv <vps-public-ip> 8081   # expect: connection refused / timed out
nc -zv <vps-public-ip> 5432   # expect: connection refused / timed out
nc -zv <vps-public-ip> 443    # expect: succeeded
```

If `ss -tlnp` shows PostgreSQL or the backend bound to `0.0.0.0`, ufw is a
second layer of defense, not a substitute for fixing the bind address:
PostgreSQL should listen on `localhost` only (`listen_addresses = 'localhost'`
in `postgresql.conf`) and the backend already binds to all interfaces on
`8081` per `application.yaml`'s `server.port: 8081` (no `server.address` is
set) — see `docs/operations/nginx.md` for the optional
`server.address: 127.0.0.1` change and its risk.

**Rollback:**

```bash
sudo ufw disable
```

## 2. SSH hardening

Use a drop-in file instead of editing `/etc/ssh/sshd_config` directly, so a
single `rm` fully reverts the change.

```bash
sudo tee /etc/ssh/sshd_config.d/99-hardening.conf > /dev/null <<'EOF'
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
MaxAuthTries 3
EOF
```

> ⚠️ **Lock-out risk.** `PasswordAuthentication no` disables password login
> entirely. Before reloading, confirm your key-based login works and that the
> `VPS_USER` used by GitHub Actions (`.github/workflows/deploy.yml`) also
> authenticates via `VPS_SSH_KEY`, never a password. Keep your current
> session open; test the reload from a **second** session.

```bash
sudo sshd -t                        # validate syntax BEFORE reloading — must print nothing
sudo systemctl reload ssh           # (or `sshd`, depending on distro/unit name)
```

**Verify (from a second session):**

```bash
ssh -o PreferredAuthentications=password -o PubkeyAuthentication=no <user>@<vps-host>
# expect: "Permission denied (publickey)" — password auth is rejected
ssh <user>@<vps-host>    # your normal key-based login still works
```

**Rollback:**

```bash
sudo rm /etc/ssh/sshd_config.d/99-hardening.conf
sudo sshd -t && sudo systemctl reload ssh
```

## 3. Scoped sudo for the CI deploy user

`deploy.yml` restarts the backend via SSH as `VPS_USER` running
`systemctl restart sevillasinluz`. That user needs `sudo` for exactly that one
command — nothing else.

```bash
sudo tee /etc/sudoers.d/sevillasinluz-deploy > /dev/null <<'EOF'
# Allow the CI deploy user to restart only the sevillasinluz service, no password.
<VPS_USER> ALL=(root) NOPASSWD: /usr/bin/systemctl restart sevillasinluz
EOF
sudo chmod 440 /etc/sudoers.d/sevillasinluz-deploy
sudo visudo -c    # validates ALL files in sudoers.d — must report "parsed OK"
```

Replace `<VPS_USER>` with the actual `VPS_USER` secret value, and update
`deploy.yml`'s SSH step to `sudo systemctl restart sevillasinluz` if the
deploy user is not already allowed to run that command without sudo.

**Verify:**

```bash
sudo -l -U <VPS_USER>   # should list exactly the systemctl restart command, NOPASSWD
```

**Rollback:**

```bash
sudo rm /etc/sudoers.d/sevillasinluz-deploy
```

## 4. fail2ban

```bash
sudo apt-get install -y fail2ban
sudo cp infra/fail2ban/jail.local /etc/fail2ban/jail.local
sudo systemctl enable --now fail2ban
```

The `nginx-limit-req` jail requires the rate-limit zones from
`infra/nginx/rate-limit.conf` (see `docs/operations/nginx.md`) to be active
first, since it bans IPs based on 429 responses in nginx's error log.

**Verify:**

```bash
sudo fail2ban-client status
sudo fail2ban-client status sshd
# confirm the bundled filter exists before relying on the jail:
test -f /etc/fail2ban/filter.d/nginx-limit-req.conf && echo "filter present"
sudo fail2ban-client status nginx-limit-req
```

**Rollback:**

```bash
sudo rm /etc/fail2ban/jail.local
sudo systemctl restart fail2ban   # falls back to jail.conf defaults (mostly disabled)
```

## 5. Unattended upgrades

```bash
sudo apt-get install -y unattended-upgrades apt-listchanges
sudo dpkg-reconfigure -plow unattended-upgrades   # answer "Yes"
```

**Verify:**

```bash
sudo unattended-upgrade --dry-run --debug 2>&1 | tail -20
systemctl is-enabled unattended-upgrades.service
```

**Rollback:**

```bash
sudo systemctl disable --now unattended-upgrades.service
sudo apt-get remove -y unattended-upgrades
```

## 6. Installing the nginx hardening files

See `docs/operations/nginx.md` for the CSP construction rationale and the
full reference server config. Install order:

```bash
sudo cp infra/nginx/rate-limit.conf /etc/nginx/conf.d/rate-limit.conf
sudo mkdir -p /etc/nginx/snippets
sudo cp infra/nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
sudo cp infra/nginx/security-headers-api.conf /etc/nginx/snippets/security-headers-api.conf
# Manually add the two `include snippets/...` lines to your real server blocks
# (diff infra/nginx/sevillasinluz.conf.example against your actual config first).
sudo nginx -t && sudo systemctl reload nginx
```

**Rollback:** remove the `include` lines you added, delete the copied files,
`nginx -t && systemctl reload nginx`.

## Checklist

- [ ] ufw: default deny incoming, OpenSSH + 80 + 443 allowed, enabled
- [ ] `ss -tlnp` + external `nc -zv` confirm 8081/5432 are not externally reachable
- [ ] `/etc/ssh/sshd_config.d/99-hardening.conf` installed, `sshd -t` clean, reloaded
- [ ] key-based login verified from a second session before closing the first
- [ ] scoped sudoers entry for the CI deploy user, `visudo -c` clean
- [ ] fail2ban installed with `sshd` + `nginx-limit-req` jails active
- [ ] unattended-upgrades enabled and dry-run tested
- [ ] nginx hardening files installed, `nginx -t` clean, reloaded
