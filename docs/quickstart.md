# Quick Start

This guide helps you get Kairos running quickly for local testing or small deployments.

## Choose Your Path

- Run from source with Maven Wrapper (best for development)
- Run with Docker image (fastest runtime setup)
- Run with Helm chart on Kubernetes (cluster deployments)

---

## Prerequisites

- Java 17+ (for source run)
- Docker (for container run)
- Kubernetes 1.20+ and Helm 3+ (for chart install)

---

## Option 1: Run from Source

```bash
git clone https://github.com/wenisch-tech/Kairos.git
cd Kairos
./mvnw spring-boot:run
```

Open:

```text
http://localhost:8080
```

Default credentials on first startup:

| Email | Password |
|-------|----------|
| `admin@kairos.local` | `admin` |

Change the default password immediately after first login in **Admin -> Users**.

---

## Option 2: Run with Docker

```bash
docker run -d \
  --name kairos \
  -p 8080:8080 \
  -v kairos-data:/app/data \
  ghcr.io/wenisch-tech/kairos:latest
```

Open `http://localhost:8080` and log in with the same default admin credentials shown above.

---

## Option 3: Run with Helm (Kubernetes)

Add chart repo and install:

```bash
helm repo add wenisch-tech https://charts.wenisch.tech
helm repo update
helm install kairos wenisch-tech/kairos -n kairos --create-namespace
```

Check release status:

```bash
helm status kairos -n kairos
```

For local cluster access, use port-forward:

```bash
kubectl port-forward -n kairos svc/kairos 8080:8080
```

Then open `http://localhost:8080`.

---

## First Steps After Login

1. Change the default admin password.
2. Add your first resource in **Admin -> Manage Resources**.
3. Create or edit a resource group and set its visibility (`PUBLIC`, `AUTHENTICATED`, `HIDDEN`) as needed.
4. Trigger a manual check on the resource detail page.
5. Optionally create an API key in **Admin -> API Keys** for automation.

---

## Common Next Tasks

- Runtime and database tuning: [configuration.md](configuration.md)
- Resource auth patterns: [authentication.md](authentication.md)
- API usage and payloads: [api.md](api.md)
- Import/export resources: [importexport.md](importexport.md)
- Docker registry pullability behavior: [docker-pullability.md](docker-pullability.md)
- Common failures and fixes: [troubleshooting.md](troubleshooting.md)
