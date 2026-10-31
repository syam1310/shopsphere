#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}. Create it from .env.production.example, then set the local required values." >&2
  exit 1
fi

for required_key in DB_PASSWORD JWT_SECRET INTERNAL_SERVICE_SECRET; do
  if ! grep -Eq "^[[:space:]]*${required_key}=" "${ENV_FILE}"; then
    echo "${required_key} must be set in ${ENV_FILE}." >&2
    exit 1
  fi
done

kubectl create secret generic shopsphere-secrets \
  --namespace shopsphere \
  --from-env-file="${ENV_FILE}" \
  --dry-run=client \
  --output=yaml \
  | kubectl apply --filename=- >/dev/null

echo "Applied shopsphere-secrets in namespace shopsphere (values were not printed)."
