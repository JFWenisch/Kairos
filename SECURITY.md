# Security Policy

## Supported Versions

Security fixes are applied to the **latest major version** only. Older versions are not backported.

| Version | Supported |
|---------|-----------|
| Latest release | ✅ |
| <  v2.0 | ❌ |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

Use the [GitHub private vulnerability reporting](https://github.com/wenisch-tech/Kairos/security/advisories/new) feature to disclose issues confidentially.

Include as much of the following information as possible to help understand and reproduce the issue:

- Type of vulnerability (e.g. XSS, CSRF, authentication bypass, SSRF, information disclosure)
- The affected component or endpoint
- Step-by-step instructions to reproduce the issue
- Proof-of-concept code or screenshots, if available
- Potential impact assessment

You can expect an initial response within **5 business days**. If the issue is confirmed, a fix will be prioritised based on severity.

## Scope

The following are considered in scope:

- The Kairos Spring Boot application (`src/`)
- The Helm chart (`charts/kairos/`)
- The official container image (`ghcr.io/wenisch-tech/kairos`)

Out of scope:

- Vulnerabilities in third-party dependencies that have no available fix
- Issues in forks or unofficial builds
- Findings from automated scanners without a demonstrated exploitable impact

## Security Design Notes

Kairos is a **self-hosted** application. You are responsible for the security of the environment it runs in. Key things to be aware of:

- **Default credentials** — the default admin account (`admin@kairos.local` / `admin`) must be changed immediately after the first login. Kairos reminds you of this on the admin panel.
- **Network exposure** — do not expose the admin panel (`/admin/**`) to the public internet without additional access controls (e.g. a reverse proxy with IP allowlisting or VPN).
- **API keys** — treat API keys like passwords. Revoke any key that is no longer needed.
- **SMTP credentials** — notification provider credentials (SMTP passwords, webhook URLs, tokens) are stored in the database. Ensure your database storage is appropriately secured.
- **OIDC** — when using an external identity provider, ensure the provider is correctly configured to restrict access to authorized users only.
- **Container image signing** — official container images are signed with [cosign](https://docs.sigstore.dev/cosign/overview/). Verify the signature before deploying in production environments.

## Dependency Updates

Dependencies are reviewed and updated regularly. Container images are rebuilt on a regular schedule to include upstream OS and runtime patches.
