#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
compose=(docker compose --parallel 1 -p shopsphere-load -f "$repo_root/docker-compose.yml" -f "$repo_root/docker-compose.load.yml")
products="${LOAD_TEST_PRODUCTS:-1000}"
users="${LOAD_TEST_USERS:-1000}"
[[ "$products" =~ ^[1-9][0-9]{0,6}$ ]] || { echo 'LOAD_TEST_PRODUCTS must be between 1 and 9,999,999.' >&2; exit 2; }
[[ "$users" =~ ^[1-9][0-9]{0,6}$ ]] || { echo 'LOAD_TEST_USERS must be between 1 and 9,999,999.' >&2; exit 2; }

# The catalog test needs only auth, gateway, products, and inventory. Keeping
# order/cart/payment/notification stopped avoids exhausting a small Docker VM.
"${compose[@]}" up -d --build user-service api-gateway product-service inventory-service
wait_for_table() {
  local service="$1" database="$2" table="$3"
  for _ in $(seq 1 90); do
    if "${compose[@]}" exec -T "$service" psql -U shopsphere -d "$database" -Atqc \
      "SELECT to_regclass('public.${table}') IS NOT NULL" 2>/dev/null | grep -q '^t$'; then
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for $database.$table schema initialization." >&2
  return 1
}
wait_for_table product-db product_db products
wait_for_table inventory-db inventory_db inventory
wait_for_table user-db user_db users
"${compose[@]}" exec -T product-db psql -v ON_ERROR_STOP=1 -v product_count="$products" -U shopsphere -d product_db \
  < "$repo_root/scripts/load-test/seed-products.sql"
"${compose[@]}" exec -T inventory-db psql -v ON_ERROR_STOP=1 -v product_count="$products" -U shopsphere -d inventory_db \
  < "$repo_root/scripts/load-test/seed-inventory.sql"
"${compose[@]}" exec -T user-db psql -v ON_ERROR_STOP=1 -v user_count="$users" -U shopsphere -d user_db \
  < "$repo_root/scripts/load-test/seed-users.sql"

echo "Isolated load-test stack is running at http://localhost:18080 (gateway)."
echo "Synthetic products: $products; synthetic users: $users."
echo 'Normal ShopSphere database volumes were not used.'
