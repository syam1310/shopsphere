#!/usr/bin/env bash
set -euo pipefail

# Dump every ShopSphere PostgreSQL database as a private, checksummed archive.
# Uses local socket auth inside each container; no password is printed or needed.
backup_root="${BACKUP_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/shopsphere/backups}"
container_prefix="${DB_CONTAINER_PREFIX:-shopsphere}"
now="$(date -u +%Y%m%dT%H%M%SZ)"
backup_dir="$backup_root/$now"
mkdir -p "$backup_dir"
chmod 700 "$backup_root" "$backup_dir"
umask 077

specs=(
  "user:user_db"
  "product:product_db"
  "inventory:inventory_db"
  "order:order_db"
  "cart:cart_db"
  "payment:payment_db"
  "notification:notification_db"
)
for spec in "${specs[@]}"; do
  service="${spec%%:*}"
  database="${spec#*:}"
  container="${DB_CONTAINER_NAME:-$container_prefix-$service-db}"
  archive="$backup_dir/$database.dump"
  docker exec "$container" pg_dump -U shopsphere -d "$database" --format=custom --no-owner --no-privileges > "$archive"
  test -s "$archive"
  docker exec -i "$container" pg_restore --list < "$archive" >/dev/null
done
(
  cd "$backup_dir"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum ./*.dump > SHA256SUMS
  else
    shasum -a 256 ./*.dump > SHA256SUMS
  fi
)
chmod 600 "$backup_dir"/*
printf 'Backup complete: %s (7 database archives; checksum manifest included)\n' "$backup_dir"
