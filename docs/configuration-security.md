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
