# ShopSphere on local Kubernetes

This is a local development deployment of the existing ShopSphere application. It includes the frontend, API gateway, all seven Spring services, and the seven separate PostgreSQL databases used by the Compose setup. It does not include the Compose monitoring stack, TLS ingress, autoscaling, or production-grade secret management. Payment remains the existing simulated flow.

## Requirements

- Docker Desktop with its Kubernetes cluster enabled, or another local Kubernetes cluster with a default persistent-volume storage class.
- `kubectl` and Docker CLI.
- At least 8 GB memory assigned to Docker/Kubernetes is recommended. Seven JVM services and seven databases make this a comparatively heavy local demo.
- A root `.env` file with `DB_PASSWORD`, `JWT_SECRET`, and `INTERNAL_SERVICE_SECRET` defined. Keep it private; the secret script never prints its contents. `GOOGLE_CLIENT_ID`, `ADMIN_SETUP_CODE`, and mail settings are optional.

The manifests use a dedicated `shopsphere` namespace and separate persistent claims for each database. They do not publish database ports or mutate any existing Compose database volumes.

## Build and start

From the repository root:

```sh
docker context show
kubectl config current-context
```

If you are using Docker Desktop, enable its Kubernetes cluster in Docker Desktop settings and select its Kubernetes context before continuing. These commands should show an active local cluster; do not run `kubectl apply` against a remote context by accident.

Build all application images (the script packages each Java service with its own Maven wrapper):

```sh
./k8s/build-images.sh
```

Create the namespace and application configuration, then create/update the Kubernetes Secret from the ignored root `.env` file:

```sh
kubectl apply -f k8s/namespace.yaml
./k8s/create-secrets.sh
kubectl apply -k k8s/
```

Wait for deployments and databases:

```sh
kubectl -n shopsphere get pods
kubectl -n shopsphere get pvc
kubectl -n shopsphere rollout status deployment/frontend --timeout=5m
kubectl -n shopsphere rollout status deployment/api-gateway --timeout=5m
```

The Java services start with a Kubernetes startup probe that allows up to six minutes for initialization and database migrations. If a pod does not become ready, inspect its events and logs:

```sh
kubectl -n shopsphere describe pod POD_NAME
kubectl -n shopsphere logs deployment/SERVICE_NAME
```

Open the storefront through a local port-forward:

```sh
kubectl -n shopsphere port-forward service/frontend 5173:80
```

Then browse to <http://localhost:5173>. Stop the port-forward with Ctrl+C.

## Validate manifests without deploying

These commands render and validate the Kubernetes resources locally without changing a cluster:

```sh
kubectl kustomize k8s/ >/dev/null
kubectl apply --dry-run=client --validate=false -k k8s/
```

The second command may still need a reachable API server on some kubectl versions. `kubectl kustomize` is the offline syntax/render check.

## Stop and remove

Stop application/database pods while preserving database PVCs:

```sh
kubectl -n shopsphere scale deployment --all --replicas=0
kubectl -n shopsphere scale statefulset --all --replicas=0
```

Remove the Kubernetes deployment and its database claims only when you intentionally want to delete the local Kubernetes data:

```sh
kubectl delete namespace shopsphere
```

Deleting the namespace deletes its PVCs and the data stored in those PVCs. This does not delete the separate Docker Compose volumes.

## Limitations

- This is a local developer deployment, not a production release. Kubernetes Secrets are only base64-encoded by default; configure encryption at rest and an external secret manager before production use.
- Each database and service has one replica. No HA, autoscaling, backup/restore, ingress/TLS, network policies, or monitoring stack is configured here.
- PostgreSQL starts with fresh PVCs. It does not import data from the existing Compose volumes.
- The app images use the repository's existing Dockerfiles. Image hardening and non-root runtime support remain separate production tasks.
