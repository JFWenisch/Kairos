# Embed Status Widget

Kairos provides an embeddable status widget that can be included in external HTML pages.

## What Is An Embed Widget?

An embed widget is a small, reusable UI element that you can place on another website or application (for example a company portal, status page, or documentation site).

In Kairos, the embed widget is loaded through an iframe and shows a compact live status summary from your Kairos instance.

Typical use cases:

- Show current service health on a public website
- Show internal system status in an intranet portal
- Reuse one centralized status source across multiple pages

The widget supports:

- Global scope (all resources)
- Group scope (single group)
- Configurable refresh, mode, text size and colors
- Optional scope label visibility
- Automatic parent background and text color detection (via generated snippet)

## Endpoint

- Widget URL: `/embed/status`

Basic iframe example:

```html
<iframe
  src="https://kairos.example.com/embed/status?refresh=30&mode=light&fontSize=15"
  title="Kairos Service Status"
  allowtransparency="true"
  width="360"
  height="56"
  style="border:0;overflow:hidden;background:transparent;"
  loading="lazy">
</iframe>
```

## URL options

The embed endpoint supports these query parameters:

- `refresh` (optional): refresh interval in seconds, range `10..3600`, default `30`
- `mode` (optional): `light` or `dark`, default `light`
- `fontSize` (optional): base font size in pixels, range `6..32`, default `15`
- `fontColor` (optional): hex text color, for example `#ffffff` or `#0f172a`
- `bgColor` (optional): hex background color used inside the widget document
- `groupId` (optional): render status for a specific group only
- `showScope` (optional): `true`/`false`, default `false`; controls whether the scope label is shown in widget text

Examples:

Global widget:

```html
<iframe src="https://kairos.example.com/embed/status?mode=dark&fontSize=18&refresh=30" title="Kairos Service Status" allowtransparency="true" width="360" height="56" style="border:0;overflow:hidden;background:transparent;" loading="lazy"></iframe>
```

Group widget:

```html
<iframe src="https://kairos.example.com/embed/status?groupId=12&mode=light&refresh=30" title="Kairos Group Status" allowtransparency="true" width="360" height="56" style="border:0;overflow:hidden;background:transparent;" loading="lazy"></iframe>
```

Group widget with visible scope label:

```html
<iframe src="https://kairos.example.com/embed/status?groupId=12&showScope=true&refresh=30" title="Kairos Group Status" allowtransparency="true" width="360" height="56" style="border:0;overflow:hidden;background:transparent;" loading="lazy"></iframe>
```

## Automatic parent style matching

When embedding into external pages, transparent iframes can still appear visually mismatched depending on host styles.

The admin embed generator and group copy buttons now generate a script-based snippet that:

- Detects nearest non-transparent ancestor background color
- Detects nearest ancestor text color
- Passes those values to widget URL as `bgColor` and `fontColor`

Use this generated snippet when you want the widget to blend automatically with the host container.

## What the widget shows

The widget is based on active outages:

- Green indicator + "All systems operational" when no active outages exist
- Red indicator + "Active problems detected" when one or more active outages exist

Link target behavior:

- Global widget links to dashboard (`/`)
- Group widget links to the selected group view (`/groups/{id}`)

Scope label:

- Hidden by default
- Displayed only when `showScope=true`

## Admin configuration

Open:

- `Admin -> Embed Widget`

This page provides:

- Ready-to-copy embed snippet
- Live preview
- Widget scope selector (all resources or a specific group)
- Scope label toggle (default off)
- Mode, font size, font color, refresh controls
- Embed access policy configuration
- Allowlist management for embed origins
- Required cross-origin configuration for external embeds

## Group view widget tools

In group view, the header includes:

- Live widget preview for the current group
- Copy button for group embed snippet
- Info button with embed requirements and docs link

## Embed access policies

You can choose one of three policies:

1. `DISABLED`

- Embedding is disabled
- The widget endpoint is blocked for embedding

2. `ALLOW_ALL`

- Any external origin can embed the widget

3. `ALLOW_LIST`

- Only configured origins may embed the widget
- Origins are managed on the same admin page
- Origins must use full origin syntax, for example `https://status.example.com`

Default policy for new setups is `ALLOW_ALL`.

## Security behavior

Kairos applies clickjacking protection headers as follows:

- Non-embed pages keep `X-Frame-Options: SAMEORIGIN`
- Embed page uses `Content-Security-Policy: frame-ancestors ...` derived from embed policy

Notes:

- `X-Frame-Options` cannot represent multi-origin allowlists
- Allowlist mode is therefore enforced using CSP `frame-ancestors`

## CORS and embedding notes

Embedding across origins requires correct browser/security setup:

- CORS and embed access must be configured by an admin in Settings before cross-origin embedding works
- In Kairos, configure this in `Admin -> Embed Widget` (policy and allowed origins)
- Configure embed policy (`ALLOW_ALL` or `ALLOW_LIST`) appropriately
- In `ALLOW_LIST` mode, ensure parent page origin exactly matches a configured origin (scheme, host, and port)
- If your deployment uses additional CORS/front-proxy controls, ensure they do not block iframe loading

## Troubleshooting

If embedding does not work as expected:

- Verify embed policy is not `DISABLED`
- In `ALLOW_LIST` mode, verify parent origin is configured exactly
- Check browser console for CSP `frame-ancestors` violations
- Confirm reverse proxy/CDN does not override security headers
- If colors still look off, use the generated auto-detection snippet instead of a plain static iframe
