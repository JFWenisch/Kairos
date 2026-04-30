# Instant Check

Instant Check lets users run a one-off check for a target without creating a monitored resource.

Use this when you want to quickly validate reachability or pullability before deciding to track a target permanently.

## What It Does

- Runs an immediate check for a selected resource type (`HTTP` or `DOCKER`)
- Returns result details in a modal
- Does not store a new resource automatically
- Optionally lets users convert the checked target into a tracked resource

## Configuration

All Instant Check settings are configured in:

- `Admin -> Settings -> General Settings -> Instant Check`

### Available Settings

| Setting | Description |
|---|---|
| Enable instant check form on landing page | Enables the dedicated Instant Check panel on the dashboard. |
| Allow public instant checks | If enabled, unauthenticated users can run Instant Check. If disabled, authentication is required. |
| Use stored authentication mappings for instant checks | Reuses configured auth mappings (same wildcard matching behavior as regular checks). |
| Allowed domains / targets (one entry per line) | Restricts which targets can be checked. `*` allows all targets. |

### Allowed Domains / Target Rules

- One rule per line
- `*` means allow all targets
- Wildcards are supported, for example:
  - `example.com/*` for that domain and paths
  - `example.com` for exact domain-style matching

If a target does not match any configured rule, Instant Check is rejected.

## Access Model

Instant Check execution depends on two things:

1. Feature enabled by admin
2. Caller permissions:
   - Public users allowed if `Allow public instant checks` is enabled
   - Otherwise user must be authenticated

## Expected Results

The result modal shows:

- Status: `AVAILABLE`, `NOT_AVAILABLE`, or `UNKNOWN`
- Latency
- Return code
- Checked at timestamp
- Detailed message (shown only for non-successful results)

### Status Interpretation

- `AVAILABLE`: Check succeeded
- `NOT_AVAILABLE`: Endpoint/target responded but failed availability criteria
- `UNKNOWN`: Check could not be classified reliably (for example network/TLS/runtime edge cases)

### Return Code

`Return code` is the protocol/service result code reported by the checker.

Typical examples:

- HTTP checks: `200`, `503`, etc.
- Docker checks: checker-specific error context when available

## Type-Specific Notes

### HTTP

- Uses HTTP GET with timeout
- Supports optional TLS skipping (`Skip TLS`)
- Can use stored auth mappings when enabled

### Docker

- Validates manifest and layer/blob pullability
- Supports optional TLS skipping (`Skip TLS`)
- Can use stored auth mappings when enabled

## Track As Resource

After a result is shown, users can click `Track As Resource` (if allowed by permissions).

The action pre-fills the resource submission form with:

- Resource type
- Target
- Skip TLS value

Then the user can submit and start regular scheduled monitoring for that target.

## Related Docs

- [Configuration Overview](configuration.md)
- [Authentication](authentication.md)
- [Docker Pullability](docker-pullability.md)
- [REST API](api.md)
