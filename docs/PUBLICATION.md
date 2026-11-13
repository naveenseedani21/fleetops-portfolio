# Public portfolio and frontend preview

The owner approved a public, sanitized copy on 2026-11-13. This repository retains the original 23 development commits with rewritten identifiers: account-related CI details and a private screenshot were removed, and commit email metadata was replaced with a GitHub noreply address. The original private repository was not rewritten or made public. Historical private CI links may be inaccessible; use this repository's own Actions page for independently verifiable results.

The audit inspected all 270 original Git blobs using credential patterns and reviewed findings manually. Matches were loopback/test credentials or source expressions; no live provider credential was identified. The private billing screenshot was excluded from every public revision. This is a scoped review, not a guarantee that automated scanning detects every possible secret. Public realm passwords/client secrets are deliberate local-only fixtures and must never protect an internet-facing deployment.

## Build separation

The normal `npm run build` still enters `src/main.ts`, with the real OAuth, HTTP, API and routing configuration. The preview enters `src/main.preview.ts` and imports no Auth, Api, HTTP client or OAuth provider. It uses shared Operations styles/icons and a separate standalone component. It sends no reports, does not authenticate, does not persist browser state, and contains no service credentials. Reload resets its synthetic illustration.

Build from frontend with the existing lockfile:

```sh
npm ci
npm run build -- --configuration production,preview
```

Output: `frontend/dist/preview/browser`. Only `preview-public` assets are copied, so local `config.json` and the Keycloak issuer are absent. Do not publish the regular application's output as this preview. Do not add a mock auth bypass to the real app. Sites receives only the preview build and its reproducible source.

Two preview unit tests verify provider-free boot/disclosure and independent lifecycle/reset behavior. All 22 frontend tests, lint and both builds passed locally. Desktop/mobile browser verification checked the real preview's navigation, corrected state, viewer illustration, evidence gallery, and375px page width. Actual authenticated acceptance remains the separate real Docker suite; preview interactions are not security evidence.

## $0 publication

Public GitHub uses standard Linux runners; no paid deployment or artifact service is enabled. Sites uses existing included Pro hosting. No AWS resources, domains, databases, paid upgrades or cloud backend are provisioned. A recorded video is not claimed available; genuine screenshots and executable local instructions are included.
