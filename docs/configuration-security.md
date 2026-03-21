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

## Default Credentials

First startup creates:

- `admin@kairos.local` / `admin`

Change this password immediately after first login.

## Credential Storage Note

Resource credentials are stored in the application database. Restrict DB access and use platform-level encryption controls where possible.
