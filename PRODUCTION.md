# Production deployment foundation

This repository now includes a production ingress overlay. It is a safer
deployment starting point, not a certification that ShopSphere is ready to
accept real customer orders.

## What the production overlay changes

- Runs the frontend as a static Nginx site and keeps client API requests on the
  same origin.
- Uses Caddy for HTTPS and certificate renewal. Set a real DNS name in
  `SHOPSPHERE_DOMAIN` and point its A/AAAA records at the host first.
- Publishes only ports 80 and 443. Databases and application services have no
  host-published ports and remain reachable on the private Compose network.
- Removes the shared `.env` file injection from application containers and
  passes each service only its configured environment values.
- Restarts containers after process exits.

## Configure and start

1. Create the host `.env` from `.env.production.example`. Use unique random
   values for every secret. Keep this file readable only by the deployment
   account; do not commit it or send it in logs or chat.
2. Configure the domain's DNS and allow inbound TCP 80/443 (and UDP 443 for
   HTTP/3). For Google sign-in, add the production site origin to the Google
   OAuth client's authorized JavaScript origins.
3. Build all service JARs, then validate and start the overlay:

   ```bash
   for service in user-service product-service inventory-service order-service cart-service payment-service notification-service api-gateway; do
     (cd "$service" && ./mvnw -DskipTests package)
   done
   docker compose -f docker-compose.yml -f docker-compose.production.yml config --quiet
   docker compose -f docker-compose.yml -f docker-compose.production.yml up -d --build
   ```

4. Open `https://$SHOPSPHERE_DOMAIN`. Do not use `docker compose down -v` on a
   live deployment.

## Release blockers to resolve before real production traffic

- The current versioned PostgreSQL baselines and the order V2 constraint
  migration have been exercised in an isolated restore drill, but the baselines
  still need application-owner review against a staging copy before a production
  release. Keep a verified backup before the first migration-enabled deploy.
- Backups, restore drills, retention, database encryption, and a managed secret
  store still need to be configured for the chosen host/provider. Compose
  environment variables are convenient for a small deployment but are not a
  dedicated secret-management system.
- Payment is intentionally simulated. Orders must not be treated as paid or
  used for real fulfillment until a payment provider and its verified
  callbacks/reconciliation are designed and implemented.
- The attempted 25-VU local run is invalid as a performance result: Docker
  killed Product Service containers for memory pressure and every measured
  request returned HTTP 503. Do not treat it as a capacity result. Repeat a
  bounded local run with adequate host memory, then test the actual production
  host, database, network, and service replicas before setting capacity
  promises.
- Add automated release checks, dependency and container image scanning,
  centralized logs, and recovery runbooks. Configure the host firewall so only
  the HTTPS ingress is publicly reachable.

Keep the base `docker-compose.yml` for local development. The production file is
an overlay and must be supplied together with it in every Compose command.

## Database migrations

Each database-owning service now has a Flyway `V1__initial_schema.sql` matching
its existing PostgreSQL schema. The first migration is intentionally a baseline:
existing non-empty databases are baselined at version 1, while empty databases
run V1. `order-service` V2 widens the inventory compensation status constraint
to include the current `PROCESSING` state. Hibernate runs with `ddl-auto:
validate`, so it will fail startup if entity mappings do not match the migrated
schema instead of silently changing it.

Before the first production release:

1. Take and verify a backup before deploying the migration-enabled images.
2. Review the baseline and any new V2+ SQL against a staging copy.
3. Start the service and confirm Flyway reports successful migration and the
   service health check is `UP`.
4. Never edit a migration that has already been applied; add the next numbered
   migration instead.

## Backups and restore drills

`./scripts/db/backup-all.sh` creates one custom-format PostgreSQL archive per
service database, restricts local file permissions, and writes SHA-256 checksums.
The default destination is outside the repository under the user's local state
folder. Set `BACKUP_DIR` to a protected destination for scheduled jobs. Treat
archives as sensitive because they can contain personal data. Copy production
archives to encrypted off-host/object storage and set retention appropriate to
your recovery objectives; this script does not upload or prune backups.

Run a restore drill with:

```sh
BACKUP_DIR=/secure/shopsphere-backups ./scripts/db/backup-all.sh
./scripts/db/restore-drill.sh /secure/shopsphere-backups/<timestamp>
```

The drill verifies checksums and restores every database into a temporary
PostgreSQL container backed by tmpfs. It never targets the normal Compose
containers or a persistent volume. Schedule this drill regularly and record the
restore duration and the newest verified backup.

## Monitoring and alerting

The production overlay includes Prometheus, Alertmanager, Blackbox Exporter,
and Grafana. It records service/JVM metrics and probes each service's health
endpoint (including database health). Alerts cover unavailable services, high
HTTP 5xx rate, and sustained high JVM heap. Grafana/Prometheus/Alertmanager are
bound only to host loopback; use SSH port forwarding for administration. The
Grafana dashboard is provisioned from `monitoring/grafana/dashboards`.

Create a Slack incoming webhook URL, save only that URL in a file readable by
the deployment account, set `ALERTMANAGER_SLACK_WEBHOOK_FILE` to its path, and
set a unique `GRAFANA_ADMIN_PASSWORD` in the production `.env`. Do not commit
either value. Alerts will not send notifications until the webhook file is
configured. The monitoring stack shares the application host, so it cannot
alert if that host or its network is completely down; add an external uptime
monitor for that failure mode.

## Local load rehearsal

For a bounded local-only test, use the isolated `shopsphere-load` stack and
synthetic data described in `scripts/load-test/README.md`. It uses separate
PostgreSQL volumes and the runner refuses non-local targets. The result is a
local benchmark only. It cannot be used as a capacity claim for a cloud host or
for millions of concurrent users; repeat the run against the final host with a
distributed load generator before publishing any capacity target.
