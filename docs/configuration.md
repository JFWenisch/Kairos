# Advanced Configuration

This document covers all configuration options for Kairos beyond the basics in the [README](../README.md).

---

## Database

### H2 (default — file-based, no setup required)

```properties
spring.datasource.url=jdbc:h2:file:./kairos;AUTO_SERVER=TRUE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
```

The H2 web console is available at `http://localhost:8080/h2-console` when `spring.h2.console.enabled=true` (the default).

### PostgreSQL

Add the following to `application.properties` (or pass as environment variables):

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/kairos
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=kairos
spring.datasource.password=secret
spring.jpa.hibernate.ddl-auto=update
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
```

Or with environment variables:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/kairos
SPRING_DATASOURCE_USERNAME=kairos
SPRING_DATASOURCE_PASSWORD=secret
```

A `docker-compose` example with PostgreSQL:

```yaml
version: "3.9"
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: kairos
      POSTGRES_USER: kairos
      POSTGRES_PASSWORD: secret
    volumes:
      - pgdata:/var/lib/postgresql/data

  kairos:
    image: ghcr.io/jfwendisch/kairos:latest
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/kairos
      SPRING_DATASOURCE_USERNAME: kairos
      SPRING_DATASOURCE_PASSWORD: secret
    depends_on:
      - db

volumes:
  pgdata:
```

### Automatic schema migration

Kairos uses Flyway for startup migrations. On an existing database without Flyway history, Kairos automatically creates a baseline and then applies pending migrations. No manual SQL steps are required for normal upgrades.

---

## OIDC / OAuth2 Authentication

Kairos supports OpenID Connect in addition to local username/password login. When OIDC is enabled, a **"Login with OIDC"** button appears on the login page alongside the standard form.

### Environment variables

| Variable | Required | Description |
|----------|----------|-------------|
| `OIDC_ENABLED` | Yes | Set to `true` to activate OIDC |
| `OIDC_CLIENT_ID` | Yes | Client ID registered with your IdP |
| `OIDC_CLIENT_SECRET` | Yes | Client secret registered with your IdP |
| `OIDC_ISSUER_URI` | Yes | Base URI of the OIDC issuer |

### Keycloak example

```bash
OIDC_ENABLED=true
OIDC_CLIENT_ID=kairos
OIDC_CLIENT_SECRET=<your-secret>
OIDC_ISSUER_URI=https://keycloak.example.com/realms/myrealm
```

Kairos constructs the OIDC endpoints from the issuer URI using the standard OpenID Connect discovery paths:

| Endpoint | Path appended to issuer URI |
|----------|-----------------------------|
| Authorization | `/protocol/openid-connect/auth` |
| Token | `/protocol/openid-connect/token` |
| User info | `/protocol/openid-connect/userinfo` |
| JWK set | `/protocol/openid-connect/certs` |

The redirect URI registered in the IdP must be:

```
https://<your-kairos-host>/login/oauth2/code/oidc
```

Users who log in via OIDC are automatically provisioned with the `USER` role. Promote them to `ADMIN` via **Admin → Users** if needed.

---

## Check Intervals and Parallelism

Check intervals and parallelism are configured per resource type via **Admin → Resource Types** (UI) or directly in the database.

| Resource Type | Default Interval | Default Parallelism |
|---------------|-----------------|---------------------|
| HTTP | 1 minute | 5 threads |
| DOCKER | 3600 minutes (60 h) | 2 threads |
| DOCKERREPOSITORY | 60 minutes | 1 thread |

- **Check Interval (minutes)** — how often Kairos checks all resources of this type. The scheduler polls every 30 seconds; if `now − lastRun ≥ interval`, checks are dispatched.
- **Parallelism** — number of concurrent check threads for this resource type.

For `DOCKERREPOSITORY` resources, Kairos does not create direct check results. Instead, each run synchronizes discovered repository images into generated `DOCKER` resources inside an auto-created group and removes no-longer-existing ones.

> **Note:** Kairos always runs an immediate check pass on startup, regardless of the configured interval. This ensures fresh status data is available as soon as the application is ready.

---

## Docker / OCI Registry Checks (Socketless)

Kairos checks Docker resources directly against the registry HTTP API and validates pullability by probing manifest and blob endpoints. It does **not** require Docker Engine, Podman, CRI access, or a mounted socket.

For hardened Kubernetes deployments, this means no `/var/run/docker.sock` mount is needed.

If a registry uses custom or self-signed TLS certificates, enable `skipTLS` on the resource to bypass certificate and hostname validation for that resource's HTTPS check.

For the exact request flow and auth behavior, see [docker-pullability.md](docker-pullability.md).

---

## Prometheus / Actuator Endpoints

The following actuator endpoints are exposed by default:

| Path | Description |
|------|-------------|
| `/actuator/health` | Application health (no auth required) |
| `/actuator/prometheus` | Prometheus metrics (no auth required) |
| `/actuator/info` | Build info |

To restrict or extend the exposed endpoints, set `management.endpoints.web.exposure.include` in `application.properties`.

### Prometheus scrape config

```yaml
scrape_configs:
  - job_name: kairos
    static_configs:
      - targets: ["kairos:8080"]
    metrics_path: /actuator/prometheus
```

### Key metric

```
kairos_resource_status{resource_name="<name>",resource_type="<HTTP|DOCKER>"}
```

| Value | Meaning |
|-------|---------|
| `1` | Resource is available |
| `0` | Resource is not available |
| `-1` | No checks have run yet (unknown) |

---

## Web Server Runtime (Tomcat)

Kairos uses Spring MVC + JPA + Thymeleaf on the servlet stack. For this workload, embedded Tomcat is generally a solid default and usually not the main bottleneck.

To tune for smaller containers, these properties are available:

| Property | Env var | Default | Purpose |
|----------|---------|---------|---------|
| `server.tomcat.threads.max` | `SERVER_TOMCAT_THREADS_MAX` | `80` | Maximum request worker threads |
| `server.tomcat.threads.min-spare` | `SERVER_TOMCAT_THREADS_MIN_SPARE` | `10` | Spare idle request threads |
| `server.tomcat.accept-count` | `SERVER_TOMCAT_ACCEPT_COUNT` | `100` | Queue length when worker threads are busy |
| `server.tomcat.max-connections` | `SERVER_TOMCAT_MAX_CONNECTIONS` | `512` | Maximum open HTTP connections |
| `server.tomcat.connection-timeout` | `SERVER_TOMCAT_CONNECTION_TIMEOUT` | `5s` | Timeout for establishing request data |
| `server.tomcat.keep-alive-timeout` | `SERVER_TOMCAT_KEEP_ALIVE_TIMEOUT` | `20s` | Keep-alive timeout for persistent connections |

HTTP response compression is enabled by default for common text payloads.

Example for a constrained container:

```bash
SERVER_TOMCAT_THREADS_MAX=40
SERVER_TOMCAT_MAX_CONNECTIONS=300
```

---

## Public Resource Submission

When **Allow public resource submission** is enabled in **Admin → General Settings**, unauthenticated users can `POST /api/resources` to add new monitoring targets. This is useful for self-registration flows but should only be enabled in trusted network environments.

---

## Security Notes

- All admin routes (`/admin/**`) require the `ADMIN` role.
- The H2 console (`/h2-console`) is only accessible with a same-origin frame policy and has CSRF disabled for that path only. Disable it in production: `spring.h2.console.enabled=false`.
- Default admin credentials are `admin@kairos.local` / `admin`. **Change this immediately after first login.**
