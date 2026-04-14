# Embed Status Widget

Kairos provides an embeddable status widget that can be included in external HTML pages via an iframe.

## Endpoint

- Widget URL: `/embed/status`
- Optional query parameter: `refresh`

Example:

```html
<iframe
  src="https://kairos.example.com/embed/status?refresh=30"
  title="Kairos Service Status"
  width="360"
  height="56"
  style="border:0;overflow:hidden;"
  loading="lazy">
</iframe>
```

`refresh` is in seconds and is clamped to `10..3600`.

## What the widget shows

The widget is based on active outages:

- Green indicator + "All systems operational" when no active outages exist
- Red indicator + "Active problems detected" when one or more active outages exist

## Admin configuration

Open the admin page:

- `Admin -> Embed Widget`

This page provides:

- Ready-to-copy iframe snippet
- Embed access policy configuration
- Allowlist management for embed origins

## Embed access policies

You can choose one of three policies:

1. `DISABLED`

- Embedding is disabled
- The widget endpoint is blocked for embedding

2. `ALLOW_ALL`

- Any external origin can embed the widget

3. `ALLOW_LIST`

- Only configured origins may embed the widget
- Origins are managed in the same admin page
- Origins must use full origin syntax, for example `https://status.example.com`

## Security behavior

Kairos applies clickjacking protection headers as follows:

- Non-embed pages keep `X-Frame-Options: SAMEORIGIN`
- Embed page uses a `Content-Security-Policy: frame-ancestors ...` directive derived from the configured embed policy

Notes:

- `X-Frame-Options` cannot represent an allowlist of multiple domains
- Therefore allowlist mode is enforced with CSP `frame-ancestors`

## Troubleshooting

If embedding does not work:

- Verify the embed policy is not `DISABLED`
- In `ALLOW_LIST` mode, confirm the parent page origin exactly matches a configured origin (scheme, host, and port)
- Check browser console for CSP `frame-ancestors` violations
- Confirm reverse proxy/CDN does not overwrite security headers
