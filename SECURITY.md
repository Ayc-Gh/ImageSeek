# Security Policy

## Supported version

Security fixes are applied to the latest published ImageSeek release and the current `main` branch.

## Reporting a vulnerability

Please prefer GitHub's private **Security Advisories** feature for vulnerabilities involving:

- arbitrary URL navigation or WebView escape,
- unsafe file/content access,
- temporary-upload URL validation bypass,
- unintended disclosure of local images or metadata,
- signing/update-chain problems,
- dependency or supply-chain compromise.

If private Security Advisories are unavailable, open a minimal GitHub issue stating that you have a security report, but do **not** include exploit details, private image URLs, credentials, signing material, or sensitive user data in the public issue.

## Security design

ImageSeek intentionally:

- declares only `android.permission.INTERNET`,
- uses Android system pickers / URI grants instead of broad storage access,
- re-encodes selected images before upload,
- disables WebView file/content access and mixed content,
- blocks non-HTTPS WebView navigation,
- disables WebView debugging in the app,
- does not include the former detailed debug logging subsystem,
- validates temporary image URLs against expected HTTPS hosts and path constraints.

Third-party search engines and temporary image hosts remain external services with their own security and privacy policies.
