#!/usr/bin/env bash
set -euo pipefail

# Restore archives to a new ephemeral PostgreSQL container. Never targets a
# running ShopSphere database or any existing Docker volume.
if [[ $# -ne 1 || ! -d "$1" ]]; then
  echo "Usage: $0 /path/to/backup/timestamp-directory" >&2
  exit 2
fi
backup_dir="$(cd "$1" && pwd)"
if command -v sha256sum >/dev/null 2>&1; then
  (cd "$backup_dir" && sha256sum -c SHA256SUMS)
else
  (cd "$backup_dir" && shasum -a 256 -c SHA256SUMS)
fi

suffix="$(date -u +%Y%m%d%H%M%S)-$$"
container="shopsphere-restore-drill-$suffix"
cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; }
trap cleanup EXIT

# tmpfs prevents the restored data from becoming a persistent named volume.
docker run --detach --rm --name "$container" \
  --env POSTGRES_USER=shopsphere \
  --env POSTGRES_PASSWORD=restore-drill-only \
  --env POSTGRES_DB=postgres \
  --tmpfs /var/lib/postgresql/data:rw,size=1g \
  postgres:17 >/dev/null
ready=0
for _ in $(seq 1 60); do
  if docker exec "$container" pg_isready -U shopsphere -d postgres >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
[[ "$ready" == 1 ]] || { echo 'Temporary restore database did not start.' >&2; exit 1; }

for database in user_db product_db inventory_db order_db cart_db payment_db notification_db; do
  docker exec "$container" createdb -U shopsphere "$database"
  docker exec -i "$container" pg_restore --exit-on-error -U shopsphere -d "$database" < "$backup_dir/$database.dump"
  table_count="$(docker exec "$container" psql -U shopsphere -d "$database" -Atc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'")"
  [[ "$table_count" =~ ^[1-9][0-9]*$ ]] || { echo "No tables restored in $database." >&2; exit 1; }
  if [[ "${RESTORE_EXPECT_FIXTURE:-0}" == "1" && "$database" == "product_db" ]]; then
    fixture_count="$(docker exec "$container" psql -U shopsphere -d "$database" -Atc "SELECT count(*) FROM products WHERE name = 'restore-drill-fixture'")"
    [[ "$fixture_count" == "1" ]] || { echo 'Synthetic backup fixture row was not restored.' >&2; exit 1; }
    printf '%s restored (%s tables; synthetic row verified)\n' "$database" "$table_count"
  else
    printf '%s restored (%s tables)\n' "$database" "$table_count"
  fi
done
printf 'Restore drill passed in isolated temporary container %s. The original databases were not changed.\n' "$container"
