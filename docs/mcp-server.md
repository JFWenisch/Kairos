# MCP Server

Kairos ships with a built-in **Model Context Protocol (MCP) server** that lets AI assistants (Claude, GitHub Copilot, VS Code agents, and others) query and control your Kairos instance directly — no manual API calls required.

The MCP server is powered by [Spring AI](https://spring.io/projects/spring-ai) and runs inside the same process as Kairos. It uses the standard **SSE (Server-Sent Events) transport**, which means any MCP client that supports HTTP/SSE can connect.

---

## Prerequisites

- Kairos 2.2.5 or later
- An **API key** created in **Admin → API Keys** (the MCP server requires authentication)
- An MCP-compatible client (Claude Desktop, VS Code with Copilot, Cursor, etc.)

---

## Authentication

The MCP server reuses Kairos's existing API key authentication. All MCP connections must present a valid API key JWT in the `Authorization` header:

```
Authorization: Bearer <your-api-key-jwt>
```

To create an API key:

1. Open **Admin → API Keys**
2. Enter a name (e.g. `claude-desktop`) and click **Create**
3. Copy the token — it is shown only once

There is no separate MCP-specific credential. The same API keys used for the REST API work for MCP.

---

## Connection Details

| Setting | Value |
|---------|-------|
| SSE endpoint | `http://<your-kairos-host>/sse` |
| Message endpoint | `http://<your-kairos-host>/mcp/message` |
| Transport | SSE (HTTP) |
| Authentication | `Authorization: Bearer <api-key-jwt>` |

---

## Available Tools

The MCP server exposes the following tools. AI clients will discover these automatically on connection.

### Read Operations

#### `listResources`

Lists all active monitored resources with their current status.

**Returns:** id, name, type (`HTTP` / `DOCKER`), target, current status (`AVAILABLE` / `NOT_AVAILABLE` / `UNKNOWN`), last checked timestamp.

---

### `getResource`

Gets details and current status of a specific resource by ID.

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | number | Numeric resource ID |

**Returns:** Full resource metadata including last check result, message, error code, and latency.

---

### `triggerCheck`

Triggers an immediate health check for a resource. The check runs asynchronously; use `getCheckHistory` to see the result.

| Parameter | Type | Description |
|-----------|------|-------------|
| `resourceId` | number | Numeric resource ID |

---

### `getCheckHistory`

Returns the last 50 check results for a resource (most recent first).

| Parameter | Type | Description |
|-----------|------|-------------|
| `resourceId` | number | Numeric resource ID |

**Returns:** status, checkedAt, message, errorCode, latencyMs per entry.

---

### `listAnnouncements`

Lists all currently active announcements visible to users.

**Returns:** id, kind (`INFORMATION` / `WARNING` / `PROBLEM`), HTML content, activeUntil.

---

### `listOutages`

Lists outages across all monitored resources.

| Parameter | Type | Description |
|-----------|------|-------------|
| `activeOnly` | boolean | `true` = ongoing only, `false` = all outages |

**Returns:** id, resourceId, resourceName, startDate, endDate, active flag.

---

### `getCheckAuditLog`

Returns the in-memory check audit log — last 200 checks across all resources since the last restart.

**Returns:** timestamp, kind (`Scheduled` / `Check Now` / `Instant Check`), resource name, target, triggeredBy, result.

> **Note:** This log is in-memory only and resets on application restart.

---

### `runInstantCheck`

Runs an ad-hoc check against a URL or Docker image reference without adding it to monitored resources.

| Parameter | Type | Description |
|-----------|------|-------------|
| `target` | string | URL or Docker image reference |
| `resourceType` | string | `HTTP` or `DOCKER` |

**Returns:** status, message, errorCode, latencyMs.

> Instant checks must be enabled in **Admin → General Settings → Instant Check**.

---

### Write Operations

#### `createResource`

Adds a new monitored resource and immediately triggers an initial check.

| Parameter | Type | Description |
|-----------|------|-------------|
| `name` | string | Display name |
| `resourceType` | string | `HTTP` or `DOCKER` |
| `target` | string | URL (HTTP) or Docker image reference (DOCKER) |
| `skipTls` | boolean | Skip TLS certificate verification |

**Returns:** the created resource object including its assigned `id`.

---

#### `deleteResource`

Permanently deletes a monitored resource and all its check history.

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | number | Numeric resource ID |

> This action cannot be undone. Outages associated with the resource are handled according to the **Delete outages on resource delete** setting in General Settings.

---

#### `createAnnouncement`

Creates a new announcement displayed to users on the status page.

| Parameter | Type | Description |
|-----------|------|-------------|
| `kind` | string | `INFORMATION`, `WARNING`, or `PROBLEM` |
| `content` | string | HTML content of the announcement |
| `activeUntil` | string | ISO-8601 expiry datetime (e.g. `2026-06-01T08:00:00`) or `null` for indefinite |

**Returns:** the created announcement object including its assigned `id`.

---

#### `deleteAnnouncement`

Permanently deletes an announcement by ID.

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | number | Numeric announcement ID |

---

## Setup Examples

### Claude Desktop

Add the following to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "kairos": {
      "type": "sse",
      "url": "http://your-kairos-host/sse",
      "headers": {
        "Authorization": "Bearer YOUR_API_KEY_JWT"
      }
    }
  }
}
```

Replace `your-kairos-host` with the hostname/IP of your Kairos instance and `YOUR_API_KEY_JWT` with the token from **Admin → API Keys**.

---

### VS Code (GitHub Copilot / Copilot Chat)

Add to your VS Code `settings.json` (or workspace `.vscode/mcp.json`):

```json
{
  "mcp": {
    "servers": {
      "kairos": {
        "type": "sse",
        "url": "http://your-kairos-host/sse",
        "headers": {
          "Authorization": "Bearer YOUR_API_KEY_JWT"
        }
      }
    }
  }
}
```

---

### Cursor

In Cursor's MCP settings, add a new server:

- **Transport**: SSE
- **URL**: `http://your-kairos-host/sse`
- **Headers**: `Authorization: Bearer YOUR_API_KEY_JWT`

---

## Example Prompts

Once connected, you can ask your AI assistant:

> *"Which of my monitored services are currently down?"*

> *"Trigger an immediate check on resource 5 and tell me the result."*

> *"Show me all active outages."*

> *"Check if https://example.com is reachable right now."*

> *"What were the last 10 checks for my nginx resource?"*

> *"Add https://api.example.com as a new HTTP resource called 'Example API'."*

> *"Delete the resource named 'Old Service'."*

> *"Post a WARNING announcement saying 'Scheduled maintenance Saturday 02:00–04:00 UTC'."*

> *"Remove the announcement with ID 3."*

---

## Configuration Reference

The MCP server is configured in `application.properties`. All properties can be overridden via environment variables using Spring Boot's standard property binding.

| Property | Default | Description |
|----------|---------|-------------|
| `spring.ai.mcp.server.enabled` | `true` | Enable or disable the MCP server |
| `spring.ai.mcp.server.name` | `kairos` | Server name reported to MCP clients |
| `spring.ai.mcp.server.version` | *(app version)* | Version reported to MCP clients |
| `spring.ai.mcp.server.type` | `SYNC` | Execution mode (`SYNC` for WebMVC) |
| `spring.ai.mcp.server.sse-message-endpoint` | `/mcp/message` | POST endpoint for MCP messages |

To disable the MCP server entirely:

```properties
spring.ai.mcp.server.enabled=false
```

Or via environment variable:

```bash
SPRING_AI_MCP_SERVER_ENABLED=false
```

---

## Security Notes

- The MCP server requires a valid API key on every request — anonymous access is not permitted.
- The `/sse` and `/mcp/**` endpoints are protected by the same Spring Security filter chain as the REST API.
- CSRF protection is disabled for MCP endpoints (they use bearer token auth, not browser sessions).
- Rate limiting is not applied by default. If you expose Kairos publicly, consider placing it behind a reverse proxy (nginx, Traefik) with connection limits on `/sse`.
- The `triggerCheck` and `runInstantCheck` tools consume check worker capacity. They are subject to the same queue limits as browser-initiated checks.
