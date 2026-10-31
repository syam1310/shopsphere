#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

services=(user-service product-service inventory-service cart-service order-service payment-service notification-service api-gateway)
for service in "${services[@]}"; do
  echo "Packaging ${service}"
  (cd "${service}" && ./mvnw -DskipTests package)
done

docker build -f Dockerfile.user-service -t shopsphere/user-service:local .
docker build -f Dockerfile.product-service -t shopsphere/product-service:local .
docker build -f Dockerfile.inventory-service -t shopsphere/inventory-service:local .
docker build -f Dockerfile.cart-service -t shopsphere/cart-service:local .
docker build -f Dockerfile.order-service -t shopsphere/order-service:local .
docker build -t shopsphere/payment-service:local ./payment-service
docker build -t shopsphere/notification-service:local ./notification-service
docker build -t shopsphere/api-gateway:local ./api-gateway
docker build -t shopsphere/frontend:local ./frontend

echo "Built the ShopSphere local images."
