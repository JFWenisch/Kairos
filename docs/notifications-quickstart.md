# Setting up Notifications

Kairos can send you an alert when a resource goes down and another when it recovers. This guide walks you through the complete setup using **Email (SMTP)** as the example, then shows how to extend it to other channels.

The full reference for every provider option is in [Notification Providers](notifications/providers.md). Scope and routing rules are explained in [Notification Policies](notifications/policies.md).

---

## Step 1 — Create a Notification Provider

A provider defines how Kairos delivers messages. Here we configure an Email provider using Gmail's SMTP relay.

1. Go to **Admin → Notification Providers**.
2. Click **New Provider**.
3. Fill in the form:

    | Field | Example value |
    |---|---|
    | **Name** | `Gmail SMTP` |
    | **Type** | `EMAIL` |
    | **SMTP Host** | `smtp.gmail.com` |
    | **SMTP Port** | `587` |
    | **Use STARTTLS** | ✔ (enabled) |
    | **SMTP Username** | `alerts@example.com` |
    | **SMTP Password** | *(your Gmail app password)* |
    | **Sender Email** | `alerts@example.com` |
    | **Recipient Email** | `team@example.com` |

4. Click **Save**.

!!! tip
    Gmail requires an **App Password** if 2-Step Verification is enabled on the account. Create one at **Google Account → Security → App Passwords**.

### Verify the Provider

On the **Notification Providers** list, click the **send test** icon (envelope with arrow) next to your new provider. Kairos will send a test email to the configured recipient. Check your inbox — if it arrives, the provider is working.

---

## Step 2 — Create a Notification Policy

A policy connects your provider to the resources and events that should trigger it.

1. Go to **Admin → Notification Policies**.
2. Click **New Policy**.
3. Fill in the form:

    | Field | Example value |
    |---|---|
    | **Name** | `All resources – Email` |
    | **Provider** | `Gmail SMTP` |
    | **Notify on outage started** | ✔ |
    | **Notify on outage ended** | ✔ |
    | **Scope** | `All Resources` |

4. Click **Save**.

This policy will now send an email every time any resource goes down or recovers.

---

## Step 3 — Trigger a Test

Add a resource that is currently unreachable (or temporarily set an existing resource's target to an invalid address), wait for the next check cycle, and verify that the email arrives. Switch it back to a working target and confirm the recovery email arrives too.

---

## What to Do Next

Now that your first provider and policy are in place, you can extend the setup:

- **Add more providers** — set up Discord, a generic webhook, or GitLab incident management in parallel. Each provider type has its own dedicated section in [Notification Providers](notifications/providers.md).
- **Narrow the scope** — switch a policy from *All Resources* to *Scoped* and pick specific resource groups or individual resources. See [Notification Policies](notifications/policies.md) for examples.
- **Stack policies** — create a second policy that also pings Discord only for your most critical resources, while the email policy continues to cover everything.
