# REST API Reference

Kairos exposes a JSON REST API under the `/api` prefix.

## Authentication

| Endpoint | Required role |
|----------|--------------|
| `GET /api/resources` | None (public) |
| `GET /api/resources/{id}` | `ADMIN` |
| `POST /api/resources` | `ADMIN` — or none when *public submission* is enabled |
| `DELETE /api/resources/{id}` | `ADMIN` |
| `GET /api/resources/{id}/history` | Any authenticated user |

Session-based authentication (cookie) is used. Log in via the browser login form or programmatically:

```bash
# Obtain a session cookie
curl -c cookies.txt -X POST http://localhost:8080/login \
  -d "username=admin%40kairos.local&*****"
```

> Note: Spring Security CSRF protection is active. Browser-initiated requests include the token automatically. For programmatic access, retrieve the CSRF token from the login page first (look for `<input name="_csrf">`), then include it in POST/DELETE requests.

---

## Endpoints

### `GET /api/resources`

Returns all **active** monitored resources.

**Response** `200 OK`

```json
[
  {
    "id": 1,
    "name": "GitHub",
    "resourceType": "URL",
    "target": "https://github.com",
    "active": true,
    "createdAt": "2024-01-15T10:00:00"
  },
  {
    "id": 2,
    "name": "nginx (Docker)",
    "resourceType": "DOCKER",
    "target": "nginx:alpine",
    "active": true,
    "createdAt": "2024-01-15T10:05:00"
  }
]
```

---

### `GET /api/resources/{id}`

Returns a single resource by ID including general resource information and latest health status.

**Response** `200 OK`

```json
{
  "id": 1,
  "name": "GitHub",
  "resourceType": "HTTP",
  "target": "https://github.com",
  "active": true,
  "createdAt": "2026-03-11T10:00:00",
  "currentStatus": "available",
  "latestCheckStatus": "AVAILABLE",
  "latestCheckedAt": "2026-03-11T10:30:00",
  "latestMessage": "HTTP 200",
  "latestErrorCode": "200"
}
```

**Response** `404 Not Found` — if the resource ID does not exist.

---

### `POST /api/resources`

Add a new resource to monitor.

**Request body**

```json
{
  "name": "My Service",
  "resourceType": "URL",
  "target": "https://example.com"
}
```

| Field | Type | Values | Required |
|-------|------|--------|----------|
| `name` | string | Any display name | Yes |
| `resourceType` | string | `URL` or `DOCKER` | Yes |
| `target` | string | Full URL or Docker image reference | Yes |

**Response** `200 OK` — the created resource object

```json
{
  "id": 3,
  "name": "My Service",
  "resourceType": "URL",
  "target": "https://example.com",
  "active": true,
  "createdAt": "2024-01-15T11:00:00"
}
```

**Example**

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/resources \
  -H "Content-Type: application/json" \
  -d '{"name":"Example","resourceType":"URL","target":"https://example.com"}'
```

---

### `DELETE /api/resources/{id}`

Soft-deletes (deactivates) a resource by ID.

**Response** `200 OK`

```json
{
  "status": "deleted"
}
```

**Example**

```bash
curl -b cookies.txt -X DELETE http://localhost:8080/api/resources/3
```

---

### `GET /api/resources/{id}/history`

Returns the full check history for a resource, sorted by most recent first.

**Response** `200 OK`

```json
[
  {
    "id": 42,
    "status": "AVAILABLE",
    "message": "HTTP 200",
    "errorCode": "200",
    "checkedAt": "2024-01-15T11:01:00"
  },
  {
    "id": 41,
    "status": "NOT_AVAILABLE",
    "message": "Connection refused",
    "errorCode": "CONNECTION_ERROR",
    "checkedAt": "2024-01-15T10:01:00"
  }
]
```

**Response** `404 Not Found` — if the resource ID does not exist.

| Field | Values |
|-------|--------|
| `status` | `AVAILABLE`, `NOT_AVAILABLE` |
| `errorCode` | HTTP status code (e.g. `200`, `503`), `CONNECTION_ERROR`, or `DOCKER_ERROR` |

---

## Resource types

### `URL`

Performs an HTTP GET to the `target` URL with a 15-second timeout. A `2xx` response is considered **AVAILABLE**; any other status code or connection error is **NOT_AVAILABLE**.

### `DOCKER`

Attempts to pull the Docker image specified in `target` (e.g. `nginx:alpine`, `ghcr.io/myorg/myimage:1.0`). A successful pull is **AVAILABLE**; any pull error is **NOT_AVAILABLE**. The image is removed from the local daemon immediately after the check to avoid disk usage.

---

## Prometheus metrics endpoint

Not part of the REST API, but useful alongside it:

```
GET /actuator/prometheus
```

Returns metrics in the Prometheus text format. No authentication required.
