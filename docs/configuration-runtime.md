# Configuration: Runtime (Tomcat)

Kairos runs on Spring MVC with embedded Tomcat.

For most installations, defaults are fine. For constrained environments, tune Tomcat settings below.

## Tomcat Tuning Properties

| Property | Env var | Default | Purpose |
|----------|---------|---------|---------|
| `server.tomcat.threads.max` | `SERVER_TOMCAT_THREADS_MAX` | `80` | Max request worker threads |
| `server.tomcat.threads.min-spare` | `SERVER_TOMCAT_THREADS_MIN_SPARE` | `10` | Min idle worker threads |
| `server.tomcat.accept-count` | `SERVER_TOMCAT_ACCEPT_COUNT` | `100` | Connection queue length |
| `server.tomcat.max-connections` | `SERVER_TOMCAT_MAX_CONNECTIONS` | `512` | Max open HTTP connections |
| `server.tomcat.connection-timeout` | `SERVER_TOMCAT_CONNECTION_TIMEOUT` | `5s` | Request connection timeout |
| `server.tomcat.keep-alive-timeout` | `SERVER_TOMCAT_KEEP_ALIVE_TIMEOUT` | `20s` | Keep-alive timeout |

Example for a smaller container:

```bash
SERVER_TOMCAT_THREADS_MAX=40
SERVER_TOMCAT_MAX_CONNECTIONS=300
```

HTTP compression for common text payloads is enabled by default.
