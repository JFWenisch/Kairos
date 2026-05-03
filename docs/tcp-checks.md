# TCP Port Checks

Kairos can monitor any TCP-reachable endpoint — databases, message brokers, custom services, or any other host that listens on a TCP port.

A TCP check opens a socket connection to the configured `host:port` target and marks the resource as `AVAILABLE` if the connection is accepted, or `NOT_AVAILABLE` if it is refused or times out.

## Target Format

```
host:port
```

Examples:

| Target | Use case |
|--------|----------|
| `postgres.internal:5432` | PostgreSQL |
| `redis.internal:6379` | Redis |
| `kafka.internal:9092` | Kafka broker |
| `smtp.example.com:25` | SMTP server |
| `192.168.1.100:8080` | Internal service by IP |

The host portion can be a DNS name or an IP address. IPv6 addresses must be enclosed in brackets, for example `[::1]:5432`.

## What Is Actually Checked

- A TCP connection to the given `host:port` is attempted with a **10-second timeout**.
- If the TCP handshake completes successfully, the resource is `AVAILABLE`. No data is sent or read after the connection is established.
- If the connection is refused, the host is unreachable, or the timeout expires, the resource is `NOT_AVAILABLE`.

This check tells you whether the port is **open and accepting connections**. It does not validate the application protocol (SQL, Redis RESP, etc.) running on that port.

## Defaults

| Setting | Default Value |
|---------|---------------|
| Check interval | 1 minute |
| Parallelism | 5 threads |
| Connect timeout | 10 seconds |
| Check history retention | 31 days (pruned every 60 minutes) |
| Outage threshold | 3 consecutive failures |
| Recovery threshold | 2 consecutive successes |
| Outage retention | 31 days (pruned every 12 hours) |

Check interval and parallelism can be adjusted in **Admin → Resource Types → TCP**.

## Adding a TCP Resource

### Via the Dashboard

1. Open the dashboard and click **Add Resource**.
2. Set **Type** to `TCP`.
3. Enter the target in `host:port` format, for example `postgres.internal:5432`.
4. Click **Save**. An immediate check runs automatically.

### Via the REST API

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/resources \
  -H "Content-Type: application/json" \
  -d '{"name":"PostgreSQL","resourceType":"TCP","target":"postgres.internal:5432"}'
```

### Via the MCP Server

```
resourceType: TCP
target: postgres.internal:5432
name: PostgreSQL
```

## Instant Check

TCP targets can be checked instantly from the dashboard without creating a resource. Select **TCP** as the resource type and enter a `host:port` target in the Instant Check panel.

## Latency

The measured latency is the time from the start of the TCP connect call until the socket is ready. DNS resolution time is included in this figure.

## TLS / SSL

TCP checks do **not** perform TLS negotiation. If you need to monitor a service that requires TLS (for example, a database over mutual TLS), consider using an HTTP check against its management or health endpoint instead.

The `skipTLS` field has no effect on TCP resources.

## Operational Notes

- TCP checks do not require any external dependencies or agent software on the monitored host.
- Firewall rules between the Kairos host and the target must allow outbound TCP to the target port.
- NAT or port-forwarding is transparent; only the final connection result matters.
