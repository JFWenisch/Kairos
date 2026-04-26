# Kairos Helm Chart

**Kairos** is a self-hosted uptime and availability monitoring application built with Spring Boot. It periodically checks whether your HTTP services and Docker images are reachable, stores a full check history, and presents the results on a clean status dashboard — with Prometheus metrics included.

This Helm chart deploys Kairos to a Kubernetes cluster.

## Features

- **HTTP monitoring** — periodic HTTP GET checks with configurable interval and parallelism
- **Latency tracking** — per-check latency with DNS, TCP, and TLS breakdown; interactive trend chart on the resource detail page
- **Docker image monitoring** — validates image pullability via the OCI/Docker Registry HTTP API (no Docker socket required)
- **Docker repository discovery** — provide a repository prefix and Kairos auto-creates resources for discovered images
- **Authentication support** — per-resource-type Basic Auth credentials with wildcard URL pattern matching
- **Status dashboard** — 24-hour timeline, uptime percentages (24 h / 7 d / 30 d), and full check history per resource
- **Outage tracking** — per-resource outage lifecycle with active outage indicators and live "since" counters
- **Resource groups** — organise resources into named groups with per-group visibility controls (`PUBLIC`, `AUTHENTICATED`, `HIDDEN`)
- **Announcement system** — publish rich-text announcements with severity levels and optional auto-expiry
- **Embeddable status widget** — lightweight iframe status badge with domain allowlist control
- **API keys** — generate and revoke named API keys for machine-to-machine access
- **YAML import / export** — versioned, forward-compatible resource exchange format
- **OIDC / OAuth2 login** — plug in any OpenID Connect provider (Keycloak, Auth0, etc.)
- **Prometheus metrics** — `kairos_resource_status` gauge per resource at `/actuator/prometheus`


## More Information

| Resource | Link |
|---|---|
| Official Website | [kairos.wenisch.tech](https://kairos.wenisch.tech) |
| Documentation | [kairos.wenisch.tech/docs](https://kairos.wenisch.tech/docs) |
| GitHub Repository | [github.com/wenisch-tech/Kairos](https://github.com/wenisch-tech/Kairos) |

## Prerequisites

- Kubernetes 1.20+
- Helm 3.0+

## Installation

### Add the Helm Repository (optional, if hosted)

```bash
# Direct installation from chart source
cd charts/kairos
```

### Install the Chart

```bash
helm install kairos . -n kairos --create-namespace
```

### Upgrade an Existing Release

```bash
helm upgrade kairos . -n kairos
```

### Uninstall the Chart

```bash
helm uninstall kairos -n kairos
```

## Configuration

All values are optional. The application uses sensible defaults, but you can customize the deployment by modifying `values.yaml` or passing values via the `--set` flag.

### Basic Examples

#### Using H2 Database (Default)

```bash
helm install kairos . -n kairos --create-namespace
```

Enables persistence to store data in `/app/data`:

```bash
helm install kairos . -n kairos --create-namespace \
  --set persistence.enabled=true \
  --set persistence.size=5Gi
```

#### Using PostgreSQL

```bash
helm install kairos . -n kairos --create-namespace \
  --set env.SPRING_DATASOURCE_URL="jdbc:postgresql://postgres:5432/kairos" \
  --set env.SPRING_DATASOURCE_USERNAME="kairos" \
  --set secrets.SPRING_DATASOURCE_PASSWORD="your-password"
```

#### Enable OIDC / OAuth2 (e.g., Keycloak)

```bash
helm install kairos . -n kairos --create-namespace \
  --set env.OIDC_ENABLED="true" \
  --set env.OIDC_ISSUER_URI="https://keycloak.example.com/realms/myrealm" \
  --set env.OIDC_CLIENT_ID="kairos" \
  --set secrets.OIDC_CLIENT_SECRET="your-secret"
```

#### Enable Ingress

```bash
helm install kairos . -n kairos --create-namespace \
  --set ingress.enabled=true \
  --set ingress.className=nginx \
  --set ingress.hosts[0].host=kairos.example.com \
  --set ingress.hosts[0].paths[0].path=/ \
  --set ingress.hosts[0].paths[0].pathType=Prefix
```

### Key Configuration Options

#### Image Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `image.registry` | `ghcr.io` | Container registry |
| `image.repository` | `jfwendisch/kairos` | Image repository |
| `image.tag` | `latest` | Image tag / version |
| `image.pullPolicy` | `IfNotPresent` | Image pull policy |

#### Service Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `service.type` | `ClusterIP` | Kubernetes service type |
| `service.port` | `8080` | Service port |
| `service.targetPort` | `8080` | Container target port |

#### Persistence Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `persistence.enabled` | `false` | Enable persistent storage |
| `persistence.size` | `1Gi` | PVC size |
| `persistence.storageClassName` | `""` | Storage class name |
| `persistence.mountPath` | `/app/data` | Mount path in container |

#### Resource Limits

| Parameter | Default | Description |
|-----------|---------|-------------|
| `resources.requests.cpu` | `100m` | CPU request |
| `resources.requests.memory` | `256Mi` | Memory request |
| `resources.limits.cpu` | `500m` | CPU limit |
| `resources.limits.memory` | `512Mi` | Memory limit |

#### Environment Variables - Database

| Env Variable | Default | Description |
|--------------|---------|-------------|
| `SPRING_DATASOURCE_URL` | *(auto H2)* | JDBC URL for database |
| `SPRING_DATASOURCE_USERNAME` | `sa` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | *(empty)* | Database password (**use secrets**) |

#### Environment Variables - OIDC / OAuth2

| Env Variable | Default | Description |
|--------------|---------|-------------|
| `OIDC_ENABLED` | `false` | Enable OIDC authentication |
| `OIDC_ISSUER_URI` | *(empty)* | OIDC provider issuer URI (e.g. Keycloak realm) |
| `OIDC_CLIENT_ID` | *(empty)* | OIDC client ID |
| `OIDC_CLIENT_SECRET` | *(empty)* | OIDC client secret (**use secrets**) |

#### Health Probes

| Parameter | Default | Description |
|-----------|---------|-------------|
| `livenessProbe.enabled` | `true` | Enable liveness probe |
| `readinessProbe.enabled` | `true` | Enable readiness probe |

### Sensitive Data (Secrets)

Use the `secrets` section in `values.yaml` or pass sensitive values via `--set`:

```bash
helm install kairos . -n kairos \
  --set secrets.SPRING_DATASOURCE_PASSWORD="db-password" \
  --set secrets.OIDC_CLIENT_SECRET="oidc-secret"
```

Secrets are stored in a Kubernetes `Secret` resource and mounted as environment variables in the pod.

### Pod Security Context

The chart runs the container as a non-root user (`uid: 65532`) with read-only filesystem where possible. Override with:

```bash
helm install kairos . -n kairos \
  --set podSecurityContext.runAsUser=1000 \
  --set podSecurityContext.fsGroup=1000
```

## Accessing the Application

### Port Forward (ClusterIP Service)

```bash
kubectl port-forward -n kairos svc/kairos 8080:8080
# Open http://localhost:8080
```

### Ingress

If ingress is enabled, access via the configured hostname (e.g., `https://kairos.example.com`).

### Default Credentials

| Email | Password |
|-------|----------|
| `admin@kairos.local` | `admin` |

> ⚠️ Change the default password immediately after first login via **Admin → Users**.

## Troubleshooting

### Check Pod Status

```bash
kubectl get pods -n kairos
kubectl describe pod -n kairos <pod-name>
```

### View Logs

```bash
kubectl logs -n kairos -f deployment/kairos
```

### Verify Configuration

```bash
kubectl get configmap -n kairos kairos -o yaml
kubectl get secret -n kairos kairos -o yaml  # (if secrets exist)
```

### Connect to H2 Console (debugging)

If using H2 and persistence is enabled:

```bash
kubectl port-forward -n kairos svc/kairos 8080:8080
# Open http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:file:/app/data/kairos
```

## Values Reference

See [values.yaml](values.yaml) for all available configuration options with comments.

## License

[GNU GPL v3.0](../../LICENSE.md)
