# Configuration Overview

This section groups settings by operational concern so teams can move from baseline setup to advanced hardening in a predictable order.

## Recommended Reading Order

1. [Scheduling and Retention](configuration-scheduling.md)
2. [Runtime (Tomcat)](configuration-runtime.md)
3. [Database](configuration-database.md)
4. [Security Concepts](configuration-security.md)
5. [OIDC / OAuth2](configuration-oidc.md)
6. [Observability](configuration-observability.md)
7. [Custom Headers](custom-headers.md)

## What This Covers

- Runtime behavior and lifecycle management for checks and outages
- Data retention for check history and closed outages
- Persistence settings for H2 and PostgreSQL
- Security controls for public access, group visibility, CORS, API keys, and OIDC
- Operational diagnostics through logs and metrics

## Related Operational Guides

- [Authentication for resource checks](authentication.md)
- [Instant Check](instant-check.md)
- [REST API reference](api.md)
- [Import / Export](importexport.md)
- [Docker pullability](docker-pullability.md)
- [Embed status widget](embed.md)
- [Announcements](announcements.md)
- [Troubleshooting](troubleshooting.md)
