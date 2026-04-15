# Resource Import / Export

Kairos supports importing and exporting monitored resources from the admin panel using YAML.

The feature is available under `Admin -> Manage Resources`.

## What It Does

- Export all configured resources into a YAML file
- Import resources from a YAML file into another Kairos instance
- Update existing resources during import when the same resource already exists
- Keep the format stable across future versions through a versioned exchange envelope

The import/export workflow covers monitored resources only.

- Resource groups, their visibility settings (`PUBLIC`, `AUTHENTICATED`, `HIDDEN`), and multi-group assignments are managed separately in **Admin -> Manage Resources**.
- The YAML exchange format carries a single `groupName` field per resource for compatibility. On export, the first assigned group name is written. On import, the resource is linked to that one group (by name, creating it if needed). Multi-group assignments must be set up manually in the admin panel after import.

## Admin Workflow

On the `Manage Resources` page you can:

- Click `Export YAML` to download all current resources
- Click `Import YAML` to upload a `.yaml` or `.yml` file
- Review the flash message after import to see how many resources were created, updated, or skipped

## Exchange Format

Current exports use a versioned envelope:

```yaml
format: kairos-resources
schemaVersion: 1
exportedAt: 2026-03-11T12:34:56
resourceCount: 2
resources:
  - name: Example Website
    resourceType: HTTP
    target: https://example.com/health
    skipTLS: false
    recursive: false
    active: true
    createdAt: 2026-03-11T12:00:00
  - name: Nginx Image
    resourceType: DOCKER
    target: nginx:latest
    recursive: false
    active: true
    createdAt: 2026-03-11T12:10:00
  - name: GHCR Namespace
    resourceType: DOCKERREPOSITORY
    target: ghcr.io/jfwenisch
    recursive: true
    active: true
    createdAt: 2026-03-11T12:15:00
```

## Compatibility Strategy

The import/export format is intentionally not tied directly to internal DTOs.

To keep future versions compatible, Kairos uses these rules:

- The YAML file contains a `schemaVersion`
- Unknown fields are ignored during import
- Older list-only YAML files are still accepted
- Common alias field names are accepted during import
- Missing optional fields do not cause the import to fail

This allows newer versions to extend the export format without immediately breaking older imports.

## Import Matching Rules

When importing, Kairos matches an existing resource by:

- `resourceType`
- `target`

If a matching resource exists:

- it is updated

If no matching resource exists:

- it is created

## Accepted Fields

Preferred fields in exported YAML:

- `name`
- `resourceType`
- `target`
- `skipTLS`
- `recursive`
- `active`
- `createdAt`
- `groupName` *(first assigned group; single-group import only — see note above)*

Additionally, the importer tolerates some alternate names for compatibility:

- `type` as alias for `resourceType`
- `url`, `endpoint`, or `image` as aliases for `target`
- `displayName` or `title` as aliases for `name`

## Error Handling

During import:

- invalid or incomplete entries are skipped
- unknown resource types are skipped
- the import summary reports created, updated, and skipped entries
- non-fatal compatibility notes may be shown after import

## Recommended Usage

- Use export before larger admin changes as a backup
- Use exported YAML to migrate resources between Kairos environments
- Prefer the exported format over manually created YAML when possible
