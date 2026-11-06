# RufiTrack — Play Console submission checklist (not submitted)
Last reviewed: 2026-11-06. This is preparation, not Google approval.

## Build
- App ID remains `com.example.mibbudget`; changing it would break upgrades and require a different Play listing.
- Target and compile SDK: 36; AGP 8.10.1 / Gradle 8.11.1. CI builds both signed APK (sideloading) and signed AAB (Play Console).
- Keep release keystore secure. The existing release signing config signs both APK and AAB using GitHub secrets. When first enrolling in Play App Signing, choose an app-signing-key approach deliberately: a Google-generated key will not match the certificate of older sideloaded APKs. If preserving seamless sideload-to-Play upgrades is essential, investigate exporting the original signing key using Google's approved enrollment process.
- Ensure the version code increases with each submitted AAB. CI uses 1000 + GITHUB_RUN_NUMBER; never reuse a Play versionCode.

## Sensitive permission declaration (requires manual Play Console review)
- Core use case: SMS-based money management / budgeting.
- `READ_SMS`: identify supported MIB/BML transaction history from locally accessible SMS.
- `RECEIVE_SMS`: recognize new supported bank transaction alerts when explicitly enabled.
- Bank statements via CSV and manual entry remain alternatives. Notification listener access is optional, for BML alerts.
- No financial data is sent to a developer server. Unsupported SMS are ignored; extracted transactions remain on-device.
- Demo review path: initial setup → Explore a read-only demo; no account, statement, or permission is needed to explore. Demo mode never writes data.
- Prepare a screen recording showing onboarding, the optional SMS rationale, import enable/disable, transaction screens and budget management.
- Google alone decides eligibility. Do not misrepresent SMS access or assert approval.

## Policy, store listing and legal checks
- Publish `docs/privacy.html` on a public, accessible, HTTPS URL (GitHub Pages or independent host); verify the URL is working before pasting into Play Console. The file existing in the repository is NOT sufficient.
- Review privacy wording in light of actual shipped networking, dependencies and data retention. Add a real support/contact email and a developer identity before publishing.
- Fill Data safety with genuine answers, including all SDK behavior and data controlled by the user, and complete Financial features and permissions declarations.
- Decide whether the app supports account creation (currently no developer-hosted account). Provide data deletion instructions in the listing.
- Capture screenshots from a running build (not generated mockups); verify 512×512 icon, 1024×500 feature graphic and required screenshots.
- Provide reviewer instructions for demo mode and optional real-device SMS testing. Avoid real banking screenshots or card/account numbers in store listing.
- Test Android 16 edge-to-edge behavior, permission dialogs, Android 8–16, balance reconciliations, biometrics, CSV first-run and update-over-existing-app retaining data.
- For newly created personal Play developer accounts, follow Play Console's closed-testing requirements before applying for production.

## Suggested listing draft
**App name:** RufiTrack
**Short description:** Track expenses, budgets and bank transactions in MVR.
**Description:** RufiTrack helps you organize spending, create budgets and review your financial activity in Maldivian rufiyaa (MVR). Import bank statements, manually record purchases and optionally recognize supported MIB and BML transaction messages. Use category insights, projected balances and statement reconciliation tools to understand your money. Data is stored locally; projected balances are estimates and must be checked against your bank.

## Remaining human actions
- Register or log in to Play Console, complete identity and organization verification.
- Decide Play App Signing key configuration.
- Publish the real privacy page and confirm policy contact.
- Obtain SMS permission exception approval and complete questionnaires.
- Upload AAB, install-test delivery, run closed test, review Play Console's warnings, request production when eligible.
