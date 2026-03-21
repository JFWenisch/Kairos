# Troubleshooting

This page lists common issues and fast fixes for Kairos deployments.

## Startup and Access

### Application does not start

Symptoms:

- Process exits immediately
- Spring Boot startup fails with datasource or migration errors

Checks:

1. Confirm Java version is 17+:
   ```bash
   java -version
   ```
2. Confirm configuration values are present and valid:
   - `SPRING_DATASOURCE_URL`
   - `SPRING_DATASOURCE_USERNAME`
   - `SPRING_DATASOURCE_PASSWORD`
3. Review startup logs for `Flyway`, `datasource`, or `bind` errors.

Typical fixes:

- Use a valid datasource URL for your database type.
- Ensure the DB user can connect and has schema permissions.
- Verify no other process already uses port `8080`.

---

### Web UI is unreachable

Symptoms:

- Browser cannot connect to `http://localhost:8080`
- Connection timeout from outside container/cluster

Checks:

1. Verify Kairos is running and listening on 8080.
2. For Docker, confirm port mapping:
   ```bash
   docker ps
   ```
3. For Kubernetes, confirm service and pod health:
   ```bash
   kubectl get pods -n kairos
   kubectl get svc -n kairos
   ```

Typical fixes:

- Add or correct Docker port mapping (`-p 8080:8080`).
- Use `kubectl port-forward -n kairos svc/kairos 8080:8080` for local access.
- Check ingress host/path rules if using ingress.

---

## Authentication and Login

### Cannot log in as admin

Symptoms:

- Invalid credentials message on login page

Checks:

1. Verify you are using the expected account and password.
2. If this is first startup, try default admin credentials from quickstart.
3. Confirm OIDC settings are correct if OIDC is enabled.

Typical fixes:

- Correct misconfigured OIDC environment variables.
- Disable OIDC temporarily to validate local login behavior.
- Recreate environment with known credentials if this is a disposable setup.

---

### API requests return 401/403

Symptoms:

- `401 Unauthorized` or `403 Forbidden` for protected endpoints

Checks:

1. Verify endpoint access requirements in [api.md](api.md).
2. If using API key JWT, ensure header is correct:
   - `Authorization: Bearer <token>`
3. For session auth, include CSRF token for write operations.

Typical fixes:

- Regenerate API key in Admin -> API Keys.
- Use an admin account/key for admin-only endpoints.
- Include CSRF token for `POST`, `PUT`, and `DELETE` with session auth.

---

## Monitoring and Check Results

### Resource always shows unknown or stale status

Symptoms:

- Resource remains unknown (`-1`) or does not refresh

Checks:

1. Verify resource type interval and parallelism in Admin -> Resource Types.
2. Trigger a manual check from the resource detail page.
3. Confirm target endpoint or registry is reachable from Kairos runtime network.

Typical fixes:

- Lower interval or increase parallelism for busy environments.
- Fix DNS, firewall, or proxy restrictions.
- Correct invalid target URL/image references.

---

### HTTP resource fails with TLS errors

Symptoms:

- Certificate or hostname validation errors

Checks:

1. Validate certificate chain and hostname externally.
2. Confirm target URL uses the expected certificate.

Typical fixes:

- Use valid certificates in production.
- For internal/self-signed setups, enable `skipTLS` only if acceptable for your risk profile.

---

### Docker resource fails although image exists

Symptoms:

- Docker resource marked unavailable
- Pullability-related errors

Checks:

1. Confirm image reference format and tag/digest.
2. Review credential matching rules in [authentication.md](authentication.md).
3. Review registry pullability behavior in [docker-pullability.md](docker-pullability.md).

Typical fixes:

- Add or correct Docker credentials for registry scope.
- Ensure token has `pull` permission.
- Disable `skipTLS` unless required; if required, verify registry cert setup.

---

## Data and Import/Export

### YAML import skips resources

Symptoms:

- Import summary shows skipped entries

Checks:

1. Validate YAML structure and required fields.
2. Ensure `resourceType` and `target` are valid.
3. Review format details in [importexport.md](importexport.md).

Typical fixes:

- Export first from a working instance and use that file as template.
- Correct unknown resource types or malformed entries.

---

## Metrics and Observability

### Prometheus cannot scrape metrics

Symptoms:

- Scrape target down
- Missing `kairos_resource_status` series

Checks:

1. Verify endpoint is reachable:
   - `/actuator/prometheus`
2. Verify scrape config `metrics_path` is `/actuator/prometheus`.
3. Confirm network policy/firewall allows access.

Typical fixes:

- Correct Prometheus target/port/path.
- Expose actuator endpoint through service/ingress as needed.

---

## Upgrade Issues

### Problems after upgrading Kairos

Checks:

1. Read startup logs for Flyway migration errors.
2. Confirm database backup exists.
3. Validate all runtime env vars after deployment update.

Typical fixes:

- Roll back to previous image/version if startup fails.
- Resolve migration prerequisites, then redeploy.
- Re-apply working configuration values.

---

## Collect Useful Debug Information

Before opening an issue, collect:

1. Kairos version/tag and deployment method (source, Docker, Helm)
2. Relevant startup/runtime log excerpts
3. Database type (H2/PostgreSQL)
4. Sanitized configuration values
5. Exact failing endpoint/resource target and error message
