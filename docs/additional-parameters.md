# Additional Parameters

This page highlights frequently tuned parameters after first startup.

For a complete reference, continue with the linked configuration pages.

## Retention and Cleanup

Retention runs in dedicated background jobs and can be changed in **Admin -> General Settings**.

| Setting | Default | Description |
|---|---|---|
| Check history cleanup interval | 60 minutes | How often old check results are pruned |
| Check history retention | 31 days | Deletes check results older than this threshold |
| Outage cleanup interval | 12 hours | How often closed outages are pruned |
| Outage retention | 31 days | Deletes closed outages with `endDate` older than this threshold |

See [Scheduling and Retention](configuration-scheduling.md) for lifecycle details.

## Scheduling and Outage Lifecycle

Resource-type execution and outage opening/closing thresholds are configured in **Admin -> Resource Types**.

| Setting | Typical Value | Scope |
|---|---|---|
| Check interval | 1-3600 minutes | Per resource type |
| Parallelism | 1-10 threads | Per resource type |
| Outage threshold | 3 | Per resource type |
| Recovery threshold | 2 | Per resource type |

See [Scheduling and Retention](configuration-scheduling.md).

## Common Runtime Parameters

These values are usually provided in `application.properties` or environment variables.

| Area | Typical Parameter | Notes |
|---|---|---|
| Server port | `server.port` | HTTP port for Kairos |
| Context path | `server.servlet.context-path` | Useful behind reverse proxies |
| Time zone | `user.timezone` (JVM) | Keep consistent across nodes |
| H2/PostgreSQL | `spring.datasource.*` | Persistence backend settings |

See [Runtime (Tomcat)](configuration-runtime.md) and [Database](configuration-database.md).

## Security and Access Controls

Key controls are available in **Admin -> General Settings**:

- Allow public access
- Allow public resource submission
- Allow public Check Now
- Always display URL
- CORS allowed origins

See [Security Concepts](configuration-security.md).
