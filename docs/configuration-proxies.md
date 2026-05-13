# Configuration: Proxies

Kairos supports global proxy routing for check execution and discovery sync.

## Scope

Proxy routing applies to:

- HTTP checks
- Docker registry checks
- TCP checks
- Discovery sync outbound calls

The proxy is configured globally, not per resource. This is important when resources are created by discovery services.

## Proxy Modes

Proxy behavior is controlled by a mode and a list of target patterns.

- `BLACKLIST`: Use proxy for all targets except matching entries.
- `WHITELIST`: Use proxy only for matching entries.

Rules are matched against the full target string.

## Wildcard Matching

Use `*` in any position.

Examples:

- `*.cluster.local`
- `https://internal.example.com*`
- `registry.example.com/*`

## Admin Configuration

Open `Admin -> Proxy Configuration`.

Configure:

1. Global enable switch.
2. Routing mode (`BLACKLIST` or `WHITELIST`).
3. Target rules (one per line).
4. HTTP proxy host/port.
5. SOCKS proxy host/port.
6. Optional proxy authentication.

## Protocol Notes

- HTTP proxy is used for HTTP-based checks and discovery requests.
- SOCKS proxy is used for TCP checks.
- If proxy routing is disabled, all connections are direct.
