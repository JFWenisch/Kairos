# Notification Providers

A **notification provider** defines a single delivery channel — the server, credentials, and format used to send messages. Providers are configured once and then reused across any number of [policies](policies.md).

Navigate to **Admin → Notification Providers** to create and manage providers.

---

## Email (SMTP)

Sends outage notifications as plain-text emails via any SMTP server that supports STARTTLS or plain connections.

| Field | Description |
|---|---|
| **SMTP Host** | Hostname of the mail server, e.g. `smtp.example.com` |
| **SMTP Port** | Port number — typically `587` (STARTTLS) or `465` (implicit TLS) |
| **Use STARTTLS** | Enable STARTTLS negotiation (recommended) |
| **SMTP Username** | Login user for the mail server |
| **SMTP Password** | Login password for the mail server |
| **Sender Email** | `From:` address that appears in the delivered message |
| **Recipient Email** | `To:` address that receives the notifications |

The email subject includes the resource name and event type. The body contains the resource name, type, target, and outage start/end times.

**Example configuration:**

| Field | Value |
|---|---|
| SMTP Host | `smtp.gmail.com` |
| SMTP Port | `587` |
| Use STARTTLS | ✔ |
| SMTP Username | `alerts@example.com` |
| SMTP Password | (app password) |
| Sender Email | `alerts@example.com` |
| Recipient Email | `team@example.com` |

!!! tip
    Use an **app password** rather than your primary account password when authenticating with Google or Microsoft SMTP servers.

---

## Discord

Posts a rich embed (colored card) to a Discord channel via a webhook URL created in the channel settings.

| Field | Description |
|---|---|
| **Discord Webhook URL** | Full webhook URL from Discord channel settings, e.g. `https://discord.com/api/webhooks/…` |

- Outage start embeds are colored **red**.
- Outage end embeds are colored **green**.
- Both include the resource name, type, target, and timestamp.

**How to get the webhook URL:**

1. Open the Discord channel → **Edit Channel** → **Integrations** → **Webhooks**.
2. Click **New Webhook**, give it a name, and copy the URL.

---

## Webhook

Sends an HTTP `POST` request to any URL you specify. You control the exact JSON body via a template with placeholder substitution.

| Field | Description |
|---|---|
| **Webhook URL** | HTTP/HTTPS endpoint that receives the POST |
| **Body Template** | JSON body sent with the request; leave blank to use the default payload |

**Available placeholders:**

| Placeholder | Value |
|---|---|
| `{{event_type}}` | `OUTAGE_STARTED` or `OUTAGE_ENDED` |
| `{{resource_name}}` | Display name of the monitored resource |
| `{{resource_type}}` | `HTTP`, `DOCKER`, or `TCP` |
| `{{resource_target}}` | URL, image, or `host:port` being monitored |
| `{{outage_start}}` | ISO-8601 datetime when the outage began |
| `{{outage_end}}` | ISO-8601 datetime when the outage ended (empty for active outages) |

**Default payload** (used when the body template is blank):

```json
{
  "event": "OUTAGE_STARTED",
  "resource": "My Service",
  "type": "HTTP",
  "target": "https://example.com",
  "started": "2026-05-04T18:00:00",
  "ended": ""
}
```

The `Content-Type` header is always `application/json`.

!!! note
    The webhook call times out after 10 seconds. Failures are logged but do not block other notifications.

---

## GitLab Incident Management

Integrates with GitLab's built-in incident management. When an outage starts, Kairos opens a GitLab incident in the target project. When the outage ends, Kairos closes the incident and updates its description with the resolution time.

| Field | Description |
|---|---|
| **GitLab Base URL** | Root URL of your GitLab instance, e.g. `https://gitlab.com` |
| **Project ID or Path** | Numeric project ID (e.g. `42`) or URL path (e.g. `mygroup/myproject`) |
| **Personal Access Token** | Token with the `api` scope |

**Behaviour:**

| Event | GitLab action |
|---|---|
| `OUTAGE_STARTED` | `POST /api/v4/projects/{id}/issues` with `issue_type=incident` |
| `OUTAGE_ENDED` | `PUT /api/v4/projects/{id}/issues/{iid}` — sets `state_event=close` and updates the description with the end timestamp |

The GitLab issue IID is persisted internally so the correct incident is closed when the outage resolves, even across application restarts.

**Creating a Personal Access Token:**

1. Go to **User Settings → Access Tokens** on your GitLab instance.
2. Create a token with the **`api`** scope.
3. Copy the token value and paste it into the provider form (it is stored and masked immediately).

!!! warning
    The token requires the `api` scope. Tokens with only `read_api` cannot create or update issues.

---

## Testing a Provider

Each provider has a **Send Test** button on the providers list page. The test:

- **Email** — sends a test email to the configured recipient
- **Discord** — posts a test message embed to the channel
- **Webhook** — fires a `POST` with a sample payload
- **GitLab** — performs a `GET` on the configured project to verify connectivity and token validity (no incident is created)

Test results are shown in the admin UI as a success or error flash message.
