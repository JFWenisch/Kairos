# Kairos — Uptime Monitor

**Kairos** is a self-hosted uptime and availability monitoring application built with Spring Boot. It periodically checks whether your URLs and Docker images are reachable, stores a full check history, and presents the results on a clean status dashboard — with Prometheus metrics included.

---

## Screenshots

### Status Dashboard
![Status Dashboard](docs/img/dashboard.png)

### Resource Detail
![Resource Detail](docs/img/resource-detail.png)

### Admin — Manage Resources
![Manage Resources](docs/img/admin-resources.png)

### Admin — Resource Type Configuration
![Resource Types](docs/img/admin-resource-types.png)

---

## Features

- **URL monitoring** — HTTP GET checks with configurable interval and parallelism
- **Docker image monitoring** — pulls an image and verifies it is accessible from the configured registry
- **Instant checks on startup** — monitoring begins immediately when the application starts; no waiting for the first interval tick
- **Status dashboard** — 24-hour timeline, uptime percentages (24 h / 7 d / 30 d), and a full check history per resource
- **Admin panel** — manage resources, tune check intervals and parallelism per resource type, manage users
- **Public submission mode** — optionally allow unauthenticated users to add resources via the REST API
- **OIDC / OAuth2 login** — plug in any OpenID Connect provider (Keycloak, Auth0, etc.)
- **Prometheus metrics** — `kairos_resource_status` gauge per resource, exposed at `/actuator/prometheus`
- **H2 (default) or PostgreSQL** — switch databases with a single property change
- **Dark-mode UI** — Bootstrap 5 with Bootstrap Icons, served via WebJars (no CDN dependency)

---

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+ (or use the included `./mvnw`)
- Docker socket accessible at the default path if you want Docker image checks

### Run from source

```bash
git clone https://github.com/JFWenisch/Kairos.git
cd Kairos
./mvnw spring-boot:run
```

Open **http://localhost:8080** in your browser.

**Default credentials** (created automatically on first start):

| Email | Password |
|-------|----------|
| `admin@kairos.local` | `admin` |

> ⚠️ Change the default password immediately after first login via **Admin → Users**.

### Run with Docker

```bash
docker run -d \
  --name kairos \
  -p 8080:8080 \
  -v kairos-data:/app/data \
  ghcr.io/jfwendisch/kairos:latest
```

### Build a JAR

```bash
./mvnw package -DskipTests
java -jar target/kairos-0.0.1-SNAPSHOT.jar
```

---

## Configuration

Kairos is configured via standard Spring Boot `application.properties` or environment variables.

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:h2:file:./kairos` | JDBC URL (H2 file or PostgreSQL) |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | `sa` | Database username |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | *(empty)* | Database password |
| `OIDC_ENABLED` | `OIDC_ENABLED` | `false` | Enable OIDC / OAuth2 login |
| `OIDC_CLIENT_ID` | `OIDC_CLIENT_ID` | *(empty)* | OIDC client ID |
| `OIDC_CLIENT_SECRET` | `OIDC_CLIENT_SECRET` | *(empty)* | OIDC client secret |
| `OIDC_ISSUER_URI` | `OIDC_ISSUER_URI` | *(empty)* | OIDC issuer URI (e.g. `https://keycloak.example.com/realms/myrealm`) |

See [docs/configuration.md](docs/configuration.md) for advanced configuration including PostgreSQL setup, Docker socket access, and OIDC.

---

## REST API

The REST API is available at `/api`. The dashboard (`/` and `/resources/**`) is publicly accessible; write endpoints require an authenticated admin session.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/resources` | Public | List all active resources |
| `POST` | `/api/resources` | Admin (or Public if enabled) | Add a new resource |
| `DELETE` | `/api/resources/{id}` | Admin | Delete a resource |
| `GET` | `/api/resources/{id}/history` | Authenticated | Full check history for a resource |

See [docs/api.md](docs/api.md) for full request/response examples.

---

## Monitoring with Prometheus

Kairos exposes a Prometheus-compatible endpoint at `/actuator/prometheus`. The key metric is:

```
kairos_resource_status{resource_name="GitHub",resource_type="URL"} 1.0
```

Values: `1` = available, `0` = not available, `-1` = unknown (no checks yet).

A health endpoint is also available at `/actuator/health`.

---

## Development

```bash
# Run tests
./mvnw test

# Run with H2 console enabled (default — open http://localhost:8080/h2-console)
./mvnw spring-boot:run
```

---

## License

[MIT](LICENSE)