# ShopSphere

ShopSphere is a local microservices-based shopping application. The current setup contains five Spring Boot services and five PostgreSQL databases, with each service exposed on its own port.

> **Scope note:** This README documents the configuration and flows verified in the current development environment. It does not claim that every production-readiness requirement has been completed.

## Services and ports

| Component | Local URL / port | Database |
|---|---|---|
| User Service | `http://localhost:8081` | `user_db` |
| Product Service | `http://localhost:8082` | `product_db` |
| Inventory Service | `http://localhost:8083` | `inventory_db` |
| Order Service | `http://localhost:8084` | `order_db` |
| Cart Service | `http://localhost:8085` | `cart_db` |

PostgreSQL is exposed locally on these ports:

| Database | Host port |
|---|---:|
| User DB | `5433` |
| Product DB | `5434` |
| Inventory DB | `5435` |
| Order DB | `5436` |
| Cart DB | `5437` |

The PostgreSQL containers use the `shopsphere` database user in the current local setup. Keep passwords and tokens out of source control and shared logs.

## Prerequisites

- Docker Desktop with Docker Compose
- Java 21
- Maven (or the repository's Maven Wrapper)
- `curl` for manual API checks

## Configuration

Create or update a local `.env` file at the project root. The current Compose configuration expects these variables:

```dotenv
JWT_SECRET=<use-a-long-random-secret>
INTERNAL_SERVICE_SECRET=<use-a-separate-long-random-secret>
PRODUCT_SERVICE_URL=http://product-service:8082
INVENTORY_SERVICE_URL=http://inventory-service:8083
```

Use actual secret values only in your local `.env`; never paste them into chat, documentation, Git, or screenshots. `.env` is currently ignored by Git. If a secret or token was exposed and is still valid, rotate it.

## Build the services

The service Dockerfiles copy the packaged JAR from each service's `target/` directory. Build the JARs before building application images.

From the repository root, run the following for each service:

```bash
cd user-service && ./mvnw clean package -DskipTests && cd ..
cd product-service && ./mvnw clean package -DskipTests && cd ..
cd inventory-service && ./mvnw clean package -DskipTests && cd ..
cd order-service && ./mvnw clean package -DskipTests && cd ..
cd cart-service && ./mvnw clean package -DskipTests && cd ..
```

These commands skip automated tests for the current fast local workflow. Run your test suite before a production release.

## Start and inspect the stack

Validate the Compose file:

```bash
docker compose -p shopsphere config --quiet
```

Start or update the application services:

```bash
docker compose -p shopsphere up -d --build
```

Inspect services managed by this Compose project:

```bash
docker compose -p shopsphere ps
```

The Cart database in the current environment was originally created under a separate Compose project and connected to the `shopsphere_default` network with the `cart-db` alias. Because of that history, it may not appear in `docker compose -p shopsphere ps`; check it separately:

```bash
docker ps --filter "name=shopsphere-cart-db" \
  --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

**Do not use `docker compose down -v`** as a routine cleanup command. The `-v` flag removes Compose-managed volumes and can delete local database data.

## Health checks

Check all five application health endpoints:

```bash
for port in 8081 8082 8083 8084 8085; do
  echo "=== Port $port ==="
  curl -sS --max-time 3 "http://localhost:$port/actuator/health"
  echo
done
```

Each service returned `{"groups":["liveness","readiness"],"status":"UP"}` in the verified local run.

## Verified API flows

These flows have been manually exercised in the current development environment:

- Login and authenticated product/cart requests.
- Add an item to the cart and retrieve the cart.
- Checkout with `POST /api/orders/checkout` and the required `Idempotency-Key` header.
- Retry checkout using the **same** idempotency key and receive the existing order rather than creating a duplicate.
- Cancel a pending order using `PATCH /api/orders/{id}/status?status=CANCELLED`.
- Verify that the order's inventory reservation changes to `RELEASED`.

### Example: checkout

Checkout requires a bearer token and an idempotency key. Replace the placeholder with a token obtained from the login flow; do not commit or share real tokens.

```bash
curl -i -X POST "http://localhost:8084/api/orders/checkout" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Idempotency-Key: <UNIQUE_KEY_FOR_THIS_CHECKOUT>"
```

For a retry of the same logical checkout, reuse the same idempotency key. Use a new key for a new checkout.

### Example: cancel an order

Only cancel an order you intend to cancel. Customers may cancel their own pending orders according to the current controller logic.

```bash
curl -i -X PATCH \
  "http://localhost:8084/api/orders/<ORDER_ID>/status?status=CANCELLED" \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

The cancellation flow attempts to release the order's inventory reservations. Check the API response and reservation record to confirm the result.

## Useful database inspection commands

Inspect the order table:

```bash
docker exec -it shopsphere-order-db psql \
  -U shopsphere -d order_db \
  -c "\\dt public.*"
```

Inspect orders:

```bash
docker exec -it shopsphere-order-db psql \
  -U shopsphere -d order_db \
  -c "SELECT id, user_id, idempotency_key, status, total_amount FROM public.customer_orders ORDER BY id DESC LIMIT 10;"
```

Inspect inventory reservations:

```bash
docker exec -it shopsphere-inventory-db psql \
  -U shopsphere -d inventory_db \
  -c "SELECT reservation_id, product_id, quantity, status FROM public.inventory_reservations ORDER BY reservation_id DESC LIMIT 10;"
```

These are read-only inspection queries.

## Troubleshooting

- **A health endpoint fails:** check the service logs, for example `docker compose -p shopsphere logs --tail=100 order-service`.
- **A database connection fails:** confirm the database container is running and healthy, and that the application is using the correct Compose network hostname and database name.
- **A protected endpoint returns `401` or `403`:** check that the access token is valid and that the authenticated user has the required permission.
- **A checkout retry returns an existing order:** this is expected when the same user and idempotency key are reused.
- **A reservation remains reserved after cancellation:** inspect the order-service logs and the `inventory_compensations` table before attempting any manual database changes.

## Current status

The five application health endpoints returned `UP`. The checkout/idempotency flow and cancellation/inventory-release flow were manually verified in the current local environment. Comprehensive automated tests, production deployment, monitoring, and broader security review are not claimed as complete by this README.
