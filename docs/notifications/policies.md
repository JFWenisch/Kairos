# Notification Policies

A **notification policy** connects a [provider](providers.md) to the events and resources that should trigger it. Policies are evaluated every time an outage opens or closes, and a single outage event can match multiple policies simultaneously.

Navigate to **Admin → Notification Policies** to create and manage policies.

---

## Policy Fields

| Field | Description |
|---|---|
| **Name** | A label for the policy, e.g. `All resources – Email` |
| **Provider** | The notification provider to use when this policy fires |
| **Notify on outage started** | Toggle — triggers when a resource transitions to *unavailable* |
| **Notify on outage ended** | Toggle — triggers when a resource recovers |
| **Scope** | Controls which resources are covered (see below) |

At least one of the two event toggles must be enabled.

---

## Scope Types

### All Resources

The policy matches **every** monitored resource. This is the simplest option and requires no further configuration.

### Scoped

The policy only fires for specific resources or resource groups. Select any combination of:

- **Resource groups** — the policy fires for any resource that is a member of at least one of the listed groups
- **Individual resources** — the policy fires for these specific resources regardless of group membership

A resource matches if it appears in the individual resource list **or** belongs to any of the selected groups. Both lists are evaluated with OR logic.

---

## Evaluation Order

All matching policies are evaluated independently for each event. If three policies all match a given resource and event, all three providers are notified. A failure in one provider does not prevent the others from running.

---

## Examples

### Notify the on-call team by email for all resources

| Field | Value |
|---|---|
| Name | `On-call email – all` |
| Provider | *(your SMTP provider)* |
| Notify on outage started | ✔ |
| Notify on outage ended | ✔ |
| Scope | All Resources |

### Post to Discord only when a critical group goes down

| Field | Value |
|---|---|
| Name | `Critical – Discord alert` |
| Provider | *(your Discord provider)* |
| Notify on outage started | ✔ |
| Notify on outage ended | ✗ |
| Scope | Scoped → group: `Critical Services` |

### Open a GitLab incident for a specific resource

| Field | Value |
|---|---|
| Name | `Production API – GitLab incident` |
| Provider | *(your GitLab provider)* |
| Notify on outage started | ✔ |
| Notify on outage ended | ✔ |
| Scope | Scoped → resource: `Production API` |

---

## Tips

- You can stack policies — for example one policy that emails all outages and a second that also pings Discord for a subset of critical resources.
- Deleting a provider also removes any GitLab issue references stored for that provider, preventing orphaned incident data.
- Policies with **Scoped** coverage and no groups or resources selected effectively match nothing; add at least one group or resource after saving.
