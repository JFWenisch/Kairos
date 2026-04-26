# Custom Headers

The **Custom Headers** feature lets administrators inject arbitrary HTML or JavaScript into the `<head>` tag of every Kairos page. This is useful for adding analytics scripts, custom stylesheets, font imports, or any other head-level integrations without modifying the application source.

## Configuration

Open the admin panel and navigate to **Admin → Custom Headers**.

### Fields

| Field | Description |
|---|---|
| **Content** | The raw HTML to inject into the `<head>` of every page. Accepts any valid head-level HTML, including `<script>`, `<style>`, `<link>`, and `<meta>` tags. Leave blank to disable. |
| **Also apply to admin pages** | When checked, the content is also injected into admin panel pages. When unchecked, injection is limited to public-facing pages only (dashboard, resource detail, outages, announcements). |

Changes take effect immediately on the next page load — no restart required.

## Scope

| Page type | Injected when content is set | Injected when "Also apply to admin pages" is checked |
|---|---|---|
| Dashboard | Yes | — |
| Resource detail | Yes | — |
| Outages | Yes | — |
| Announcements | Yes | — |
| Login | Yes | — |
| Admin panel pages | No | Yes |

## Example use cases

### Analytics script

```html
<script async src="https://www.googletagmanager.com/gtag/js?id=G-XXXXXXXXXX"></script>
<script>
  window.dataLayer = window.dataLayer || [];
  function gtag(){dataLayer.push(arguments);}
  gtag('js', new Date());
  gtag('config', 'G-XXXXXXXXXX');
</script>
```

### Custom stylesheet override

```html
<style>
  :root {
    --bs-body-bg: #0d0d0d;
  }
</style>
```

### Custom font

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600&display=swap" rel="stylesheet">
```

## Security notes

- The content is injected **verbatim** without sanitisation. Only administrators can configure this setting.
- Avoid injecting untrusted third-party scripts.
- The "Also apply to admin pages" checkbox is intentionally opt-in; analytics or tracking scripts typically do not need to run on admin pages.
