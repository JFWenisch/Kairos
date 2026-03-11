# REST API Reference

Kairos exposes a JSON REST API under the `/api` prefix.

## Interactive API Documentation (Swagger UI)

An interactive API explorer is available at **`/api`** once the application is running.

```
http://localhost:8080/api
```

The UI is generated automatically from the OpenAPI specification and allows you to browse all endpoints, inspect request/response schemas, and execute requests directly in the browser. No authentication is required to view the documentation — authenticated endpoints will prompt for credentials when you use *Try it out*.

The raw OpenAPI JSON spec is available at `/v3/api-docs`.

---

## Authentication

Protected endpoints support two authentication methods:

1. **Session cookie** (`JSESSIONID`) from `/login`
2. **API key JWT** (`Authorization: Bearer <token>`) created in **Admin → API Keys**

### Session-based authentication

Log in via the browser login form or programmatically:

```bash
# Obtain a session cookie
curl -c cookies.txt -X POST http://localhost:8080/login \
  -d "username=admin%40kairos.local&*****"
```

> Note: Spring Security CSRF protection is active for session-based authentication. Browser-initiated requests include the token automatically. For programmatic access with session cookies, retrieve the CSRF token from the login page first (look for `<input name="_csrf">`) and include it in POST/PUT/DELETE requests.

### API key JWT authentication

Admins can create API keys in the Admin panel:

1. Open `Admin → API Keys`
2. Enter a key name and create it
3. Copy the token immediately (**shown only once**)

Use the token in the `Authorization` header:

```bash
curl -H "Authorization: Bearer <api-key-jwt>" http://localhost:8080/api/resources
```

You can also use the alternative header prefix:

```bash
Authorization: ApiKey <api-key-jwt>
```

### Endpoint access summary

| Endpoint | Required role |
|----------|---------------|
| `GET /api/resources` | Public |
| `GET /api/resources/{id}` | Public |
| `POST /api/resources` | `ADMIN` |
| `DELETE /api/resources/{id}` | `ADMIN` |
| `GET /api/resources/{id}/history` | Any authenticated user (session or API key) |
| `GET /api/announcements` | Public |
| `GET /api/announcements/{id}` | Public |
| `POST /api/announcements` | `ADMIN` (session or API key) |
| `PUT /api/announcements/{id}` | `ADMIN` (session or API key) |
| `DELETE /api/announcements/{id}` | `ADMIN` (session or API key) |

---

## Resource Endpoints

### `GET /api/resources`

Returns all **active** monitored resources.

**Response** `200 OK`

```json
[
  {
    "id": 1,
    "name": "GitHub",
    "resourceType": "HTTP",
    "target": "https://github.com",
    "skipTLS": false,
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

Returns a single resource by ID including general information and latest health status.

**Response** `200 OK`

```json
{
  "id": 1,
  "name": "GitHub",
  "resourceType": "HTTP",
  "target": "https://github.com",
  "skipTLS": false,
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
  "resourceType": "HTTP",
  "target": "https://example.com",
  "skipTLS": false
}
```

| Field | Type | Values | Required |
|-------|------|--------|----------|
| `name` | string | Any display name | Yes |
| `resourceType` | string | `HTTP` or `DOCKER` | Yes |
| `target` | string | Full URL or Docker image reference | Yes |
| `skipTLS` | boolean | `true` or `false`; only applies to `HTTP` resources | No |

**Response** `200 OK` — the created resource object

**Example**

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/resources \
  -H "Content-Type: application/json" \
  -d '{"name":"Example","resourceType":"HTTP","target":"https://example.com","skipTLS":true}'
```

---

### `DELETE /api/resources/{id}`

Permanently deletes a resource and all its check history.

**Response** `200 OK`

```json
{ "status": "deleted" }
```

**Example**

```bash
curl -b cookies.txt -X DELETE http://localhost:8080/api/resources/3
```

---

### `GET /api/resources/{id}/history`

Returns the full check history for a resource, sorted most recent first.

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

## Announcement Endpoints

### `GET /api/announcements`

Returns all announcements (active and inactive) ordered by creation date descending.

**Response** `200 OK`

```json
[
  {
    "id": 1,
    "kind": "WARNING",
    "content": "<p>Scheduled maintenance on Saturday.</p>",
    "createdBy": "admin@kairos.local",
    "active": true,
    "activeUntil": "2026-03-15T06:00:00",
    "createdAt": "2026-03-11T08:00:00",
    "updatedAt": "2026-03-11T08:00:00"
  }
]
```

---

### `GET /api/announcements/{id}`

Returns a single announcement by ID.

**Response** `200 OK` — announcement object (same schema as above)  
**Response** `404 Not Found` — if the ID does not exist.

---

### `POST /api/announcements`

Creates a new announcement. `createdBy` is set automatically from the authenticated user. Requires `ADMIN` role.

**Request body**

```json
{
  "kind": "INFORMATION",
  "content": "<p>New feature released.</p>",
  "active": true,
  "activeUntil": null
}
```

| Field | Type | Values | Required |
|-------|------|--------|----------|
| `kind` | string | `INFORMATION`, `WARNING`, `PROBLEM` | Yes |
| `content` | string | HTML string | Yes |
| `active` | boolean | `true` / `false` | Yes |
| `activeUntil` | datetime | ISO-8601 datetime or `null` | No |

**Response** `200 OK` — the created announcement object

**Example**

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/announcements \
  -H "Content-Type: application/json" \
  -d '{"kind":"INFORMATION","content":"<p>Hello.</p>","active":true,"activeUntil":null}'
```

---

### `PUT /api/announcements/{id}`

Fully replaces an existing announcement's fields. `createdBy` and `createdAt` are preserved. Requires `ADMIN` role.

**Request body** — same schema as `POST /api/announcements`

**Response** `200 OK` — the updated announcement object  
**Response** `404 Not Found` — if the ID does not exist.

**Example**

```bash
curl -b cookies.txt -X PUT http://localhost:8080/api/announcements/1 \
  -H "Content-Type: application/json" \
  -d '{"kind":"WARNING","content":"<p>Updated.</p>","active":false,"activeUntil":null}'
```

---

### `DELETE /api/announcements/{id}`

Permanently deletes an announcement. Requires `ADMIN` role.

**Response** `200 OK`

```json
{ "status": "deleted" }
```

**Example**

```bash
curl -b cookies.txt -X DELETE http://localhost:8080/api/announcements/1
```

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
