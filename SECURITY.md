# Security Policy

## Supported Versions

Security fixes are applied to the latest release of **Antigravity Pulse**:

| Version | Supported |
| --- | --- |
| `1.x` | Yes |

## Security Architecture Highlights

- **Zero Broad Storage Permissions**: File uploads inside the remote session use Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT`), granting read access solely to the specific files the user explicitly selects.
- **WebView Scheme Enforcement**: Only `https://` and `http://` URLs are permitted in the remote WebView; `file://`, `javascript:`, `content://`, and `intent://` navigation schemes are blocked.
- **Biometric App Lock**: Optional device biometric / credential lock protects saved remote endpoints and authentication tokens.
- **Private Cloud Sync Rules**: Firestore rules isolate each signed-in user's synced hosts and prompt templates strictly to their authenticated UID.

## Reporting a Vulnerability

Please **do not** disclose security vulnerabilities publicly in GitHub Issues.

Instead, report security concerns directly to the author:

- **Contact**: Solomon Ogar Bukie
- **Email**: [solomonogarbukie@gmail.com](mailto:solomonogarbukie@gmail.com)

Please include:
1. A description of the vulnerability and its potential impact.
2. Steps to reproduce the issue.
3. Your Android version and app version.

You can expect an acknowledgment within 72 hours and a coordinated fix once verified.
