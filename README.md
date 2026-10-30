# ShopSphere

ShopSphere is a learning and portfolio e-commerce application built with Spring Boot microservices and a React storefront. It demonstrates product discovery, customer accounts, carts, inventory reservations, checkout, order cancellation, and communication between independently owned services.

> **Payment is simulated.** The application does not charge cards, UPI accounts, or wallets. This repository contains production-oriented foundations, but ShopSphere has not been deployed and validated as a live production service. Read [PRODUCTION.md](PRODUCTION.md) before deploying.

## Contents

- [Features](#features)
- [Architecture](#architecture)
- [Customer and checkout flows](#customer-and-checkout-flows)
- [Technology](#technology)
- [Run locally](#run-locally)
- [Create the first administrator](#create-the-first-administrator)
- [Add demo catalog data](#add-demo-catalog-data)
- [Tests and verification](#tests-and-verification)
- [Operations and deployment](#operations-and-deployment)
- [API overview](#api-overview)
- [Interview preparation](#interview-preparation)
- [Known limitations](#known-limitations)
- [Troubleshooting](#troubleshooting)

## Features

### Storefront

- Browse active products without signing in.
- Search and filter by category; catalog results use numbered pagination.
- View product details, color/shade choices where available, related products, and customer reviews.
- Add products to a browser-local guest bag or use an account cart after signing in.
- Sign in with email/password or optional Google Identity Services. Google sign-in requires configuration.
- Checkout using a simulated payment method, view order history, and cancel eligible pending orders.

### Product administration

- Administrators can add products and starting stock through the storefront's **Manage products** view.
- The backend also has admin-only product update and delete endpoints; update supports deactivating a product. Those management actions are not currently exposed as a full edit/delete workflow in the storefront UI.
- Public registration creates customer accounts only. The first administrator is created using a one-time bootstrap code; see [Create the first administrator](#create-the-first-administrator).

### Reliability foundations

- Checkout accepts an idempotency key to protect retries from creating duplicate orders.
- Inventory is reserved during checkout and released on eligible cancellation.
- Failed inventory compensation is recorded so recovery/reconciliation can retry it.
- Each data-owning service has its own PostgreSQL database and Flyway migrations.
- Actuator health and Prometheus metrics are configured. Optional production Compose services include Caddy, Prometheus, Alertmanager, Blackbox Exporter, and Grafana.

## Architecture

~~~mermaid
flowchart LR
    Browser[React storefront<br/>Vite / Nginx] -->|same-origin /api| Gateway[Spring Cloud API Gateway]
    Gateway --> User[User Service]
    Gateway --> Product[Product Service]
    Gateway --> Inventory[Inventory Service]
    Gateway --> Cart[Cart Service]
    Gateway --> Order[Order Service]
    Gateway --> Payment[Payment Service<br/>simulated]
    Gateway --> Notify[Notification Service]
    User --> UserDB[(user_db)]
    Product --> ProductDB[(product_db)]
    Inventory --> InventoryDB[(inventory_db)]
    Cart --> CartDB[(cart_db)]
    Order --> OrderDB[(order_db)]
    Payment --> PaymentDB[(payment_db)]
    Notify --> NotifyDB[(notification_db)]
    Order -. REST calls .-> Inventory
    Order -. REST calls .-> Product
    Order -. REST calls .-> Cart
    Order -. simulated charge .-> Payment
    Order -. confirmation .-> Notify
~~~

The API Gateway is the storefront's API entry point. In local development, backend services publish individual ports for debugging. The production Compose overlay removes application and database host ports and routes public traffic through Caddy over HTTPS. Services use HTTP APIs over the private Compose network. There is no shared application database or cross-service SQL transaction.

### Services and local ports

| Component | Responsibility | Local port | Database |
|---|---|---:|---|
| Frontend | React/TypeScript storefront and admin screens | <code>5173</code> (Vite) | — |
| API Gateway | Routes <code>/api/**</code> to backend services | <code>8080</code> | — |
| User Service | Registration, login, JWT/refresh tokens, admin bootstrap, Google sign-in | <code>8081</code> | <code>user_db</code> |
| Product Service | Catalog, search/category filters, reviews, admin product operations | <code>8082</code> | <code>product_db</code> |
| Inventory Service | Stock availability and reservations/releases | <code>8083</code> | <code>inventory_db</code> |
| Order Service | Checkout, orders, idempotency, cancellation, compensation recovery | <code>8084</code> | <code>order_db</code> |
| Cart Service | Customer cart and items | <code>8085</code> | <code>cart_db</code> |
| Payment Service | Demo-only payment result and idempotency | <code>8086</code> | <code>payment_db</code> |
| Notification Service | In-app notifications and optional email jobs | <code>8087</code> | <code>notification_db</code> |

The base Compose file publishes the seven PostgreSQL databases on host ports <code>5433</code>–<code>5439</code> for local development. Do not expose database ports publicly. The production overlay unpublishes them.

## Customer and checkout flows

### Browse and cart

Product reads are public. The storefront calls the gateway, not individual backend services. Guests use a browser-local bag; after sign-in, the frontend syncs guest items into the account cart. Product and inventory services are authoritative for catalog and stock data.

### Checkout and cancellation

1. The shopper selects a payment *type*, such as UPI, CARD, or WALLET. No payment credentials are collected.
2. The frontend sends checkout through the gateway with an <code>Idempotency-Key</code>.
3. Order Service validates the cart/products, reserves stock, requests a simulated payment result, and persists the order.
4. A retry of the same logical checkout reuses the same key. A new checkout gets a new key.
5. If a later step fails after reservation, Order Service attempts to release the stock. Failed compensation is persisted for recovery.
6. When an eligible order is cancelled, inventory is released. Repeated cancellation must not restore the same reservation twice.

This is a saga-style workflow: services coordinate through APIs and compensating actions rather than a distributed database transaction. Temporary cross-service inconsistency is possible while a failed compensation is pending recovery.

## Technology

- Backend: Java 21, Spring Boot, Spring Security, Spring Data JPA, Spring Cloud Gateway, Maven.
- Frontend: React, TypeScript, Vite, CSS; Nginx serves the production static bundle.
- Persistence: PostgreSQL 17, one database per data-owning service, Flyway SQL migrations, Hibernate schema validation.
- Local orchestration: Docker Compose.
- Production ingress foundation: Caddy for HTTPS and certificate renewal.
- Monitoring foundation: Prometheus, Alertmanager, Blackbox Exporter, and Grafana.
- Optional integrations: Google Identity Services and SMTP email.

## Run locally

### Prerequisites

- Docker Desktop with Docker Compose.
- Java 21; service Maven Wrappers are included.
- Node.js 20+ and npm for the Vite development server.

### 1. Configure environment

Create a root <code>.env</code> file. Compose requires <code>DB_PASSWORD</code>, <code>JWT_SECRET</code>, and <code>INTERNAL_SERVICE_SECRET</code>; first-admin setup uses <code>ADMIN_SETUP_CODE</code>. Use distinct random values. [.env.production.example](.env.production.example) lists production variable names, but its placeholders are not credentials. The root <code>.env</code> is ignored by Git. Never commit or share actual secrets, access tokens, refresh tokens, or private customer data.

Google sign-in is optional. If enabled locally, set <code>GOOGLE_CLIENT_ID</code> in the root environment and <code>VITE_GOOGLE_CLIENT_ID</code> in <code>frontend/.env</code>. Authorize the Vite origin <code>http://localhost:5173</code> in Google settings.

### 2. Build service JARs

The service Dockerfiles copy packaged JARs from each service's <code>target/</code> directory:

~~~bash
for service in user-service product-service inventory-service order-service cart-service payment-service notification-service api-gateway; do
  (cd "$service" && ./mvnw clean package -DskipTests) || exit 1
done
~~~

Run tests before packaging a release; see [Tests and verification](#tests-and-verification).

### 3. Start backend and databases

~~~bash
docker compose -p shopsphere config --quiet
docker compose -p shopsphere up -d --build
docker compose -p shopsphere ps
~~~

### 4. Start the frontend

In another terminal:

~~~bash
cd frontend
npm ci
npm run dev
~~~

Open the local Vite URL, normally [http://localhost:5173](http://localhost:5173). Vite forwards <code>/api</code> to the gateway at <code>http://localhost:8080</code>.

### 5. Check service health

~~~bash
for port in 8080 8081 8082 8083 8084 8085 8086 8087; do
  printf '\n--- port %s ---\n' "$port"
  curl -fsS --max-time 5 "http://localhost:$port/actuator/health" || true
done
~~~

Actuator exposure can differ by service; investigate a failed health request in that service's configuration and logs.

> **Protect local data:** avoid <code>docker compose down -v</code>. The <code>-v</code> option removes named database volumes. Use <code>docker compose -p shopsphere down</code> to stop containers while retaining volumes.

## Create the first administrator

Admin access is not self-service for every new account. The first administrator uses a one-time bootstrap code:

1. Generate a strong code locally, for example <code>openssl rand -hex 32</code>.
2. Set it as <code>ADMIN_SETUP_CODE</code> in the root <code>.env</code> and restart User Service.
3. Open the sign-in dialog. The first-admin setup option is shown only while no administrator exists.
4. Submit the code and administrator details. The backend validates the code and checks that no admin already exists.
5. Remove the code from <code>.env</code> after setup and keep the admin credentials private.
6. Sign in; the storefront then offers **Manage products**.

Regular registration remains customer-only. Do not expose a public operation that allows users to select their own admin role.

## Add demo catalog data

The demo catalog script creates 1,000 synthetic listings across 20 categories and matching inventory rows. Product names, descriptions, prices, and stock are generated; this is not a real merchant catalog. Images are local demo assets in <code>frontend/public/product-images/</code> and may be reused by image selection logic.

With the base Compose stack running:

~~~bash
./scripts/seed-demo-catalog.sh
~~~

The product seed uses <code>ON CONFLICT DO NOTHING</code> for its IDs, so it is repeatable for those rows. It writes to the normal development database. Do not run it against production unless that has been intentionally reviewed.

For synthetic users/products and isolated database volumes, see [scripts/load-test/README.md](scripts/load-test/README.md).

## Tests and verification

Run all service tests:

~~~bash
for service in user-service product-service inventory-service order-service cart-service payment-service notification-service api-gateway; do
  echo "Testing $service"
  (cd "$service" && ./mvnw test) || exit 1
done
~~~

Some integration tests use Testcontainers and require Docker. If Docker is unavailable, those tests may be skipped; a skipped test is not a passing integration test. The suite includes unit, service, controller, and selected database integration tests. The exact test count can change.

Other checks:

- Base Compose: <code>docker compose -p shopsphere config --quiet</code>.
- Production Compose: <code>docker compose -f docker-compose.yml -f docker-compose.production.yml config --quiet</code> after providing required environment values and the protected Alertmanager webhook file.
- Frontend build: <code>cd frontend && npm ci && npm run build</code>.
- Local load test: follow [scripts/load-test/README.md](scripts/load-test/README.md).

A small local load run is not proof of cloud capacity or support for millions of concurrent users.

## Operations and deployment

### Compose profiles

- <code>docker-compose.yml</code> is intended for local development and publishes application/database ports for debugging.
- <code>docker-compose.production.yml</code> is an overlay. Always supply it with the base file. It keeps service/database ports private and exposes Caddy on ports 80/443.
- Configure a real domain and DNS before starting production Compose. Caddy obtains and renews HTTPS certificates.

High-level production start sequence, after host, DNS, secrets, firewall, and backup storage have been prepared:

~~~bash
for service in user-service product-service inventory-service order-service cart-service payment-service notification-service api-gateway; do
  (cd "$service" && ./mvnw package -DskipTests) || exit 1
done
docker compose -f docker-compose.yml -f docker-compose.production.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.production.yml up -d --build
~~~

Use [.env.production.example](.env.production.example) as a variable checklist only; replace placeholders with unique secrets on the host. See [PRODUCTION.md](PRODUCTION.md) for the full checklist and release limitations.

### Migrations, backups, and monitoring

- Flyway migrations are under each database-owning service's <code>src/main/resources/db/migration/</code>. Hibernate uses <code>ddl-auto: validate</code>; it should detect schema mismatches instead of silently changing tables.
- Review migrations on a staging copy and verify a backup before the first production migration-enabled deployment. Never edit a migration already applied; add a new version.
- <code>scripts/db/backup-all.sh</code> creates PostgreSQL archives and checksums locally. It does not upload archives or schedule itself.
- <code>scripts/db/restore-drill.sh</code> verifies checksums and rehearses restore in a temporary isolated PostgreSQL container.
- Configure encrypted off-host backup storage, retention, scheduling, and recurring restore drills on the selected host.
- Monitoring configuration lives under <code>monitoring/</code>. Alert delivery requires the protected Slack webhook file. Add external uptime monitoring because on-host monitoring cannot report if the host itself is down.

## API overview

The browser should use the API Gateway at <code>http://localhost:8080</code>, not individual service ports. Protected requests generally require <code>Authorization: Bearer &lt;access-token&gt;</code>.

| Area | Main routes | Notes |
|---|---|---|
| Authentication | <code>POST /api/users/register</code>, <code>POST /api/auth/login</code>, <code>POST /api/auth/refresh</code>, <code>POST /api/auth/logout</code> | Registration is customer-only. |
| Google sign-in | <code>POST /api/auth/google</code> | Optional; verifies Google credential against configured client ID. |
| First admin | <code>GET /api/auth/admin-setup/status</code>, <code>POST /api/auth/admin-setup</code> | One-time guarded bootstrap. |
| Products | <code>GET /api/products/catalog</code>, <code>GET /api/products/categories</code>, <code>GET /api/products/{id}</code> | Public active catalog; supports page, size, category, search. |
| Product management | <code>POST /api/products</code>, <code>PUT /api/products/{id}</code>, <code>DELETE /api/products/{id}</code> | Admin-only. Update can deactivate; DELETE removes the product record. |
| Reviews | <code>GET/POST /api/products/{productId}/reviews</code> | Read and submit product reviews. |
| Cart | <code>GET /api/cart</code>, <code>POST /api/cart/items</code>, <code>PUT/DELETE /api/cart/items/{productId}</code> | Account cart requires authentication; guest cart is browser-local. |
| Orders | <code>POST /api/orders/checkout</code>, <code>POST /api/orders/checkout/buy-now</code>, <code>GET /api/orders/my</code>, <code>GET /api/orders/{id}</code>, <code>PATCH /api/orders/{id}/status</code> | Checkout requires an idempotency key. |
| Inventory | <code>GET /api/inventory/availability</code>, <code>POST /api/inventory/reserve</code>, <code>POST /api/inventory/release</code> | Reservation routes are service operations. |
| Simulated payment | <code>POST /api/payments</code> | Demo-only; does not move money. |
| Notifications | <code>/api/notifications/**</code> | In-app notifications and internal order/email workflows. |

See controller classes for request schemas and validation. Swagger/OpenAPI is configured for selected services; the gateway is not a complete API-contract portal.

### Example: checkout and cancellation

Use a new unique key for a new checkout. Reuse the same key only when retrying that same checkout.

~~~bash
curl -i -X POST "http://localhost:8080/api/orders/checkout?paymentMethod=UPI" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Idempotency-Key: <UNIQUE_CHECKOUT_KEY>"
~~~

To cancel an eligible order:

~~~bash
curl -i -X PATCH \
  "http://localhost:8080/api/orders/<ORDER_ID>/status?status=CANCELLED" \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
~~~

Never paste real tokens or secrets into shared chat, source code, screenshots, or logs.

## Interview preparation

### 30-second introduction

> “ShopSphere is a Java 21 and Spring Boot microservices e-commerce demo with a React/TypeScript storefront. It separates identity, catalog, inventory, cart, orders, simulated payment, and notifications behind an API Gateway. Checkout demonstrates idempotency and inventory reservations with compensating actions when a later step fails. Each data-owning service has its own PostgreSQL database and Flyway migrations. It includes Docker Compose, automated tests, and deployment/monitoring foundations, but it has not been proven at production scale.”

### Architecture decisions to explain

**Why microservices?** The project demonstrates clear business boundaries and independent data ownership. Microservices also add operational overhead: more deployments and databases, network failure modes, distributed tracing needs, and more complex local development. A modular monolith could be a better starting point for a small team or product.

**Why a database per service?** Each service owns its schema and other services use APIs instead of querying its tables. This reduces schema coupling but means a workflow cannot rely on one ACID transaction across services.

**How does checkout handle retries?** An idempotency key identifies one logical checkout attempt. A retry with the same key should return or reconcile that attempt rather than create a duplicate. A new shopper action uses a new key. Explain how the implementation persists checkout state and handles failures.

**How is inventory consistency handled?** Stock is reserved during checkout. If a later operation fails, Order Service requests release from Inventory Service. Failed release attempts are recorded for retry/recovery. This is a saga/compensation pattern, not a distributed transaction; eventual inconsistency can exist while recovery is pending.

**What does payment do?** Payment is simulated; no credentials are collected and no money moves. A real integration would need a payment provider, verified callbacks/webhooks, idempotency, refunds/reconciliation, and secret management. These are intentionally out of scope.

**How is access controlled?** JWT authentication and role checks protect customer/admin operations. Product reads are public; writes require admin. First-admin bootstrap uses a one-time code and only works before any admin exists. Internal calls use a shared secret; production should use a secret manager and assess stronger service identity such as mTLS.

**How are database changes controlled?** Versioned Flyway SQL migrations own schema changes. Hibernate validates mappings rather than automatically changing a live schema. Migrations should be reviewed and rehearsed on staging with a verified backup.

**How would you scale it?** Measure against the target host first. Candidate improvements include managed PostgreSQL, replicas for stateless services, pool tuning, caching catalog reads, CDN product images, queue-based events, centralized logs/traces, and distributed load testing. Do not claim millions of users based on a bounded local benchmark.

### Practice questions

1. Walk through checkout from the browser click to order confirmation. What if payment succeeds but order persistence fails?
2. What happens if inventory release fails during cancellation? How is the failure recovered?
3. Why is an idempotency key needed? How do you decide whether two requests are the same logical checkout?
4. Why not use a single database transaction across all services?
5. How do you prevent a customer from creating an administrator account?
6. What data belongs to each service, and how do you avoid cross-service database queries?
7. Which metrics and alerts would you check if checkout starts failing?
8. What is the difference between a unit, integration, and load test?
9. What remains before this demo can accept real customer orders?
10. When would you choose a modular monolith instead?

Be transparent in interviews: this is a portfolio/demo project with production-oriented improvements, not an audited or production-proven commerce system. Payment is simulated. Deployment, host-specific backup/alert setup, and production-scale performance validation remain to be completed.

## Known limitations

- No real payment processor or money movement.
- Checkout does not currently include a shipping address/fulfillment workflow.
- No production domain/host deployment or cloud capacity result is included.
- Off-host backup scheduling, external uptime monitoring, and alert destination configuration require operator setup.
- Local load testing is bounded and does not establish millions of concurrent users.
- Seed data and local catalog imagery are demo content, not a verified commercial catalog.

## Troubleshooting

- **Service does not start:** inspect <code>docker compose -p shopsphere ps</code> and <code>docker compose -p shopsphere logs --tail=100 &lt;service-name&gt;</code>.
- **Health check fails:** inspect the service log and database health; verify required environment variables are present without printing their values.
- **Gateway returns 502/503:** confirm the backend is running and gateway URLs use Compose service hostnames inside Docker.
- **Inventory is insufficient:** product visibility does not reserve stock; final availability is checked during checkout.
- **Checkout retry returns conflict:** reuse the original key only for that same attempt. Use a fresh key for a new checkout.
- **Category returns no products:** confirm the selected category exactly matches an active product category and inspect the Product Service API/log.
- **Schema validation fails:** inspect Flyway history and the schema/entity mismatch. Do not delete the database volume; take a backup and apply a reviewed migration.
- **Never use <code>docker compose down -v</code> as a troubleshooting shortcut** if data must be preserved.

## Project documentation

- [Production deployment foundation and runbooks](PRODUCTION.md)
- [Frontend setup and customer flow](frontend/README.md)
- [Isolated synthetic load testing](scripts/load-test/README.md)
- [Change history](CHANGES.md)
