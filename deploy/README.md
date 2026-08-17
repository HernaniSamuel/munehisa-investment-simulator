# Production deployment (Hetzner Cloud)

Runs `postgres`, `backend`, `data-service`, and `caddy` (HTTPS reverse proxy) on a single
Hetzner Cloud VM. Only `caddy` is reachable from outside the VM; everything else talks over
the internal Docker network. See the root [README.md](../README.md) for local development -
this file only covers the one-time production setup.

## 1. Create the Hetzner Cloud VM

1. Sign up at [console.hetzner.com](https://console.hetzner.com) and create a project.
2. Add a payment method (card, PayPal, or SEPA). Hetzner bills monthly in arrears for what you
   provisioned that month, auto-charged to this method - see "Avoiding unexpected charges"
   below before creating anything.
3. **Servers → Add Server.**
4. **Location**: any region is fine, pricing doesn't vary by much. Pick one close to your
   audience (e.g. Falkenstein/Nuremberg for Europe, Ashburn for the US).
5. **Image**: Ubuntu 24.04 or Debian 12 - either works, the commands below assume Debian/Ubuntu
   (`apt`).
6. **Type**: a **Shared vCPU (CX)** server is enough for this stack - `CX22` (2 vCPU / 4 GB RAM)
   comfortably fits Postgres, the JVM backend, the Python data-service, and Caddy together.
   This is a flat monthly price regardless of actual CPU/RAM usage.
7. Add your SSH public key, leave the rest at defaults (the default public IPv4 is enough - no
   need for a paid Floating IP for a single, never-rebuilt server), and create the server. Note
   the public IPv4 shown after creation.

### Avoiding unexpected charges

Hetzner has no hard spending cap - it's a monthly invoice auto-charged to your payment method,
not a prepaid balance that simply stops. What keeps this predictable instead is that pricing is
**flat per server**, not metered per request/GB the way AWS is - the only way to owe more than
the server's listed monthly price is to provision something extra yourself:

- **Set a cost alert immediately**: `console.hetzner.com/usage` → set an email alert at a low
  threshold. It's a notification, not a hard stop, but it catches mistakes early.
- **Only provision the single CX22 server** from step 1. Don't add extra volumes, snapshots,
  Floating IPs, or a Load Balancer - Caddy already terminates TLS on the VM itself, and the
  server's included traffic allowance (generous, shown on the pricing page) is far more than a
  low-traffic portfolio project needs.
- Check `console.hetzner.com/usage` occasionally - it shows exactly what's accruing for the
  current billing period before the invoice is cut.

## 2. Configure the firewall

Two layers, both worth setting up:

1. **Hetzner Cloud Firewall** (recommended, applied outside the VM): Console → **Firewalls →
   Create Firewall** → allow inbound TCP `80`, `443`, and `22` (restrict `22` to your own IP if
   it's static) → attach it to the server from step 1. Unlike Oracle, Hetzner doesn't block
   ports by default, so without this every port a process listens on is reachable.
2. **OS-level firewall** (defense in depth, on the VM itself):
   ```
   sudo ufw allow 22/tcp
   sudo ufw allow 80/tcp
   sudo ufw allow 443/tcp
   sudo ufw enable
   ```

## 3. Install Docker on the VM

```
curl -fsSL https://get.docker.com | sudo sh
sudo systemctl enable --now docker
sudo usermod -aG docker $USER   # log out/in for this to take effect
```

## 4. Create the DuckDNS subdomain

1. Sign in at [duckdns.org](https://www.duckdns.org) and create a subdomain (e.g.
   `munehisa-api.duckdns.org`).
2. Point it at the VM's public IPv4 from step 1.

## 5. Configure and start the stack

On the VM:

```
git clone <this repo> && cd <this repo>/deploy
cp .env.example .env
nano .env   # fill in every value - see comments in the file for how to generate secrets
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

`backend` and `data-service` reference pre-built images on `ghcr.io` rather than building
locally (see "Set up automatic deploys" below) - the free VM has far less CPU/RAM than a CI
runner, so building on it was only ever a placeholder.

`DUCKDNS_DOMAIN` in `.env` must match the subdomain from step 4 - Caddy uses it both to route
traffic and to request the Let's Encrypt certificate for it.

Restarting the stack (`docker compose restart`) or rebooting the VM does not lose data:
`postgres_data`, `caddy_data`, and `caddy_config` are named Docker volumes, and Docker's
`unless-stopped` restart policy brings all four containers back up automatically after a
reboot (Docker itself is already enabled to start on boot from step 3).

## 6. Verify

```
curl -i https://<your-subdomain>.duckdns.org/actuator/health
curl -i -H "Origin: https://hernanisamuel.github.io" https://<your-subdomain>.duckdns.org/actuator/health
```

The first should return `200`; the second should include a matching
`Access-Control-Allow-Origin` header.

## 7. Set up uptime monitoring

Hetzner doesn't reclaim idle VMs the way some free tiers do, so this step is purely about
knowing when the service goes down - not required to keep the server alive.

1. Create a free account at [UptimeRobot](https://uptimerobot.com) (or any similar service).
2. Add an HTTP(s) monitor for `https://<your-subdomain>.duckdns.org/actuator/health`.
3. Set the check interval to **5 minutes** (the shortest on UptimeRobot's free plan) so a
   downtime alert arrives promptly.

## 8. Set up automatic deploys

`.github/workflows/deploy-backend.yml` tests, builds, and pushes `backend` and `data-service`
images to GHCR on every push to `main` that touches `src/backend/**`, `src/data-service/**`, or
`deploy/**`, then SSHes into the VM to pull and restart them. Two one-time steps make this work:

1. **Repository secrets** (Settings → Secrets and variables → Actions → New repository secret):
   - `HETZNER_VM_HOST` - the VM's public IPv4.
   - `HETZNER_VM_SSH_USER` - `root` (or whichever user owns `~/app/deploy` on the VM).
   - `HETZNER_VM_SSH_KEY` - the **private** half of a dedicated deploy key, not your personal
     one. Generate one (`ssh-keygen -t ed25519 -f deploy_key -N ""`) and add its public half to
     the VM's `~/.ssh/authorized_keys`, ideally restricted to only the deploy command:
     ```
     command="cd /root/app/deploy && docker compose -f docker-compose.prod.yml pull && docker compose -f docker-compose.prod.yml up -d",no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty ssh-ed25519 AAAA...
     ```
     This way, even if the key material in the GitHub secret ever leaks, it can only run that
     one command - not open an arbitrary shell.
2. **Make the GHCR packages public**: the first successful `build-and-push` run creates
   `ghcr.io/hernanisamuel/munehisa-investment-simulator-backend` and `...-data-service` as
   **private** packages by default. The VM's `docker compose pull` has no registry credentials,
   so each package needs its visibility changed to **Public** once (its GitHub page → Package
   settings → Change visibility) before the first automatic deploy can pull it.

After both are done, pushing to `main` (or running the workflow manually via
`workflow_dispatch`) deploys automatically - no more manual SSH + rebuild.
