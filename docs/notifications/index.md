# Notifications

Kairos can notify you when a resource goes down or comes back up. The notification system is built around two concepts that work together:

- **Providers** — define *how* a notification is delivered (Email, Discord, Webhook, GitLab)
- **Policies** — define *when* and *for which resources* a provider is triggered

## How It Works

```
Outage event
     │
     ▼
NotificationDispatchService
     │  iterates all matching policies
     ▼
NotificationPolicy  (scope check)
     │  passes? → delegate to provider
     ▼
NotificationProvider  (EMAIL / DISCORD / WEBHOOK / GITLAB)
     │
     ▼
Notification sent
```

1. When an outage opens or closes, Kairos emits an event (`OUTAGE_STARTED` / `OUTAGE_ENDED`).
2. Every policy that has the corresponding trigger enabled is evaluated.
3. If the policy scope matches the affected resource, the linked provider delivers the notification.

Failures in one provider never block notifications from other policies.

## Supported Channels

| Channel | Provider Type | Description |
|---|---|---|
| Email (SMTP) | `EMAIL` | Sends plain-text outage emails via any SMTP server |
| Webhook | `WEBHOOK` | HTTP POST with a configurable JSON template |
| Discord | `DISCORD` | Rich embed message via a Discord webhook URL |
| GitLab | `GITLAB` | Creates and closes GitLab incidents via the Issues API |

## Sections

- [Notification Providers](providers.md) — configure Email, Discord, Webhook, and GitLab providers
- [Notification Policies](policies.md) — define which providers fire for which resources and events
