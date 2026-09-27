# Security Policy

## Supported versions

Security fixes target the latest public release and the current `main` branch. Older releases may not receive separate patches.

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting entry under the repository **Security** tab. Do not disclose a vulnerability publicly before a fix or mitigation is available.

Do not attach real API keys, signing material, full Bug Reports, raw Root output, account data, serial numbers or other unique device identifiers. Build a minimal synthetic reproduction whenever possible.

Useful reports include:

- affected APA version and Android/API version;
- required permission level, Root manager or Shizuku state;
- clear reproduction steps and realistic impact;
- the smallest sanitized log or sample needed to reproduce;
- whether the issue affects local storage, cloud transmission, backup, file parsing or privileged command boundaries.

The maintainer will acknowledge actionable reports on a best-effort basis, validate the impact, and coordinate disclosure after a fix or documented mitigation. This project does not operate an APA relay server or user-account backend; vulnerabilities in third-party model providers should also be reported to the relevant provider.

## Security boundaries

- Model output is explanatory and is not executed as arbitrary shell input.
- Root diagnostics use a fixed read-only allowlist with time and output limits.
- Raw Bug Reports and raw Root output are not sent to model providers.
- Provider keys are stored with Android Keystore-backed encryption and excluded from configured backup paths.
- Shizuku installation detection is not a claim that Binder authorization or advanced Shizuku APIs are implemented.

These boundaries reduce risk but do not prove compatibility with every OEM, Root solution or backup implementation. See [testing evidence](docs/TESTING.md) and [open technical debt](docs/TECH_DEBT.md).
