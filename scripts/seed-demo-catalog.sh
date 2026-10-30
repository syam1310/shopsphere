#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

docker compose -f "$repo_root/docker-compose.yml" exec -T product-db \
  sh -lc 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < "$repo_root/scripts/seed-demo-products.sql"

docker compose -f "$repo_root/docker-compose.yml" exec -T inventory-db \
  sh -lc 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < "$repo_root/scripts/seed-demo-inventory.sql"
