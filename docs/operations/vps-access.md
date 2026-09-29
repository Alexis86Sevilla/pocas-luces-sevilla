# VPS access

The VPS (IONOS, `82.223.37.60`) hosts both `sevillasinluz.es` and `urban-oasis.info`. One SSH key gives access to the whole server.

## Current policy (since 2026-09-29)

| Setting | Value | Where |
|---|---|---|
| SSH password login | disabled | `/etc/ssh/sshd_config.d/00-hardening.conf` |
| Root login | key only (`prohibit-password`) | same file |
| Max auth tries | 3 | same file |
| Firewall (ufw) | deny incoming except 22, 80, 443 | `ufw status verbose` |

The file is named `00-…` on purpose: sshd keeps the **first** value it reads and the image ships `50-cloud-init.conf`, which enables passwords.

Keys in `/root/.ssh/authorized_keys`: one per personal machine plus the GitHub Actions deploy key.

## Emergency access (locked out of SSH)

Use the IONOS **remote console (KVM)**, which does not go through SSH:

IONOS account → Menu → Server & Cloud → Infrastructure → Servers → select the server → arrow next to **Actions** → **Open remote console**. Log in as `root` with the root password.

The root password still works there (disabling password auth only affects SSH). Keep it in a password manager and never remove it.

To undo the SSH hardening from the console:

```bash
rm /etc/ssh/sshd_config.d/00-hardening.conf && systemctl reload ssh
```

To disable the firewall from the console: `ufw disable`.

## Adding a new computer

On the new computer (PowerShell):

```powershell
ssh-keygen -t ed25519 -C "<computer-name>"
Get-Content $env:USERPROFILE\.ssh\id_ed25519.pub
```

Then add that public key line from a machine that already has access, or from the IONOS console:

```bash
echo "<paste the ssh-ed25519 ... line>" >> /root/.ssh/authorized_keys
```

Test from the new computer: `ssh root@sevillasinluz.es "echo OK"`.

## Removing a lost or retired computer

```bash
grep -n "" /root/.ssh/authorized_keys      # find the line by its comment (e.g. "alexis-pc")
sed -i '/alexis-pc$/d' /root/.ssh/authorized_keys
```

Never remove the GitHub Actions key unless you also rotate the `VPS_SSH_KEY` repository secret.
