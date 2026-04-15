# Configuration: Security

This page groups security-relevant runtime settings and operational notes.

## Access Control

- `/admin/**` routes require `ADMIN` role.
- Announcement and resource admin actions are therefore admin-only.

## H2 Console

The H2 console is intended for local usage.

- Path: `/h2-console`
- Disable in production:

```properties
spring.h2.console.enabled=false
```

## Public Resource Submission

If **Allow public resource submission** is enabled in **Admin -> General Settings**, unauthenticated users can create resources through `POST /api/resources`.

Use this only in trusted environments.

## Public Access Gate

The **Allow public access** option in **Admin -> General Settings** controls whether unauthenticated visitors can open public pages.

- Enabled (default): public pages and public endpoints remain reachable for unauthenticated users.
- Disabled: unauthenticated users are redirected to `/login`, and pages are only accessible to logged-in users.

## Resource Group Visibility

Under **Admin -> Manage Resources**, each group can be assigned a visibility mode:

- `PUBLIC`: group and resources are visible to all users.
- `AUTHENTICATED`: group and resources are visible only to authenticated users.
- `HIDDEN`: group and resources are hidden from dashboard and public resource API views.

### Multi-group assignment

A resource can belong to **more than one group**. In the resource table and on the edit page, hold **Ctrl** (Windows/Linux) or **⌘** (macOS) to select multiple groups. Deselecting all groups leaves the resource ungrouped.

When a resource belongs to multiple groups, the **most-permissive** visibility across all of its groups is applied:

| Assigned groups | Effective visibility |
|---|---|
| `PUBLIC`, `HIDDEN` | `PUBLIC` (most permissive wins) |
| `AUTHENTICATED`, `HIDDEN` | `AUTHENTICATED` |
| `HIDDEN` only | `HIDDEN` |
| None (ungrouped) | Always visible |

### Notes

- Ungrouped resources are always treated as visible.
- Group visibility is evaluated in addition to the global **Allow public access** gate above.
- If global public access is disabled, anonymous users cannot access public pages regardless of per-group visibility.
- A resource will appear in **every** group it belongs to on the dashboard.

## Public "Check Now"

If **Allow public "Check Now"** is enabled in **Admin -> General Settings**, unauthenticated users can trigger manual checks from resource detail pages.

If disabled, manual checks are still available to authenticated admins.

## URL Visibility on Dashboard and Detail Page

The **Always display URL** option in **Admin -> General Settings** controls whether unauthenticated users can see full resource targets/URLs.

- Enabled: URLs are shown on the dashboard (timeline and card view) and on resource detail pages.
- Disabled: public users see only the resource name.

Authenticated users (including admins) always see URLs, regardless of this setting.

## Default Credentials

First startup creates:

- `admin@kairos.local` / `admin`

Change this password immediately after first login.

## Credential Storage Note

Resource credentials are stored in the application database. Restrict DB access and use platform-level encryption controls where possible.

---

## API CORS Allowed Origins

When calling the Kairos REST API from a browser application hosted on a different origin (domain, scheme, or port), browsers enforce the same-origin policy and block the request unless Kairos sends the appropriate CORS headers.

### Configuration

Allowed origins are managed in **Admin → General Settings → API CORS Allowed Origins**. No restart is needed after adding or removing an entry.

- **Add**: enter a full origin (`https://example.com`) and click **Add Origin**.
- **Remove**: click the trash icon next to any entry.

### Rules

- The value must start with `http://` or `https://`.
- No trailing slash, no path component, no wildcards.
- CORS headers are only injected for requests to `/api/*` paths.
- If no origins are configured, no CORS headers are sent (secure default).
- Server-to-server calls (`curl`, backend services) are never affected.

See the [REST API reference](api.md#cors-cross-origin-resource-sharing) for detailed examples and format rules.
