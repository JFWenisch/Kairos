# Configuration: Observability

This page covers actuator and Prometheus integration.

## Default Actuator Endpoints

| Path | Description |
|------|-------------|
| `/actuator/health` | Health endpoint |
| `/actuator/prometheus` | Prometheus scrape endpoint |
| `/actuator/info` | Build/runtime info |

All of the above are exposed by default.

To customize exposure, set:

```properties
management.endpoints.web.exposure.include=health,info,prometheus
```

## Prometheus Scrape Example

```yaml
scrape_configs:
  - job_name: kairos
    static_configs:
      - targets: ["kairos:8080"]
    metrics_path: /actuator/prometheus
```

## Key Metric

```text
kairos_resource_status{resource_name="<name>",resource_type="<HTTP|DOCKER>"}
```

| Value | Meaning |
|-------|---------|
| `1` | Available |
| `0` | Not available |
| `-1` | Unknown (no check run yet) |
