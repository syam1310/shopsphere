# Isolated local load demo

This profile creates a separate Compose project (`shopsphere-load`) and separate PostgreSQL volumes named `shopsphere_load_*`. It does not connect to the normal ShopSphere DB volumes. It seeds synthetic-only product and user records; the runner uses a fixed test-only credential that must never be reused outside this isolated environment.

Start the catalog test profile with the defaults (1,000 products and 1,000 users). It starts only auth, gateway, Product Service, and Inventory Service to keep local memory use bounded:

```sh
LOAD_TEST_PRODUCTS=1000 LOAD_TEST_USERS=1000 ./scripts/load-test/start-isolated.sh
```

Then run the local bounded load runner (Node.js 20+; 10 virtual users and 20 seconds by default):

```sh
node scripts/load-test/local-load.mjs
```

The runner is restricted to localhost and at most 100 virtual users. Example:

```sh
VUS=25 DURATION_SECONDS=60 LOAD_TEST_USERS=1000 node scripts/load-test/local-load.mjs
```

Products and inventory are generated in matching ID ranges, across 20 categories, with descriptions, prices, stock, and shared local product/shade image URLs. Users get unique synthetic emails. Counts can be raised with `LOAD_TEST_PRODUCTS` and `LOAD_TEST_USERS`; watch host memory, CPU, and disk. The repository has been exercised with one million synthetic product rows, one million stock rows, and one million synthetic accounts on the current local test setup.

## Important capacity limitation

Catalog reads use a bounded page (default 48, maximum 100), and inventory availability is queried only for the IDs in that page. This reduces memory and response size for large catalogs. It is not evidence that the app can serve millions of concurrent users: the local runner caps at 100 VUs, and the tested local Compose setup used 10 and 25 VUs. Production-scale capacity requires production-like infrastructure, service replicas, tuned connection pools, caching, observability, and distributed load generation.

The isolated stack uses the same host port mapping by default in the base Compose file; the load override publishes its services on ports 18080–18087 and its databases on 15433–15439. Start the normal stack only if your Docker Compose version supports the override syntax used by the project. To stop the isolated services without deleting data:

```sh
docker compose -p shopsphere-load -f docker-compose.yml -f docker-compose.load.yml down
```

Do not add `-v` unless you intentionally want to delete the isolated load-test databases.
