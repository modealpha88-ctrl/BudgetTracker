# Midnight Emerald UI and Navigation Redesign

Status: design specification; implementation pending.

## Design tokens
- AMOLED background: #050B09; surface: #101B17; raised surface: #182720.
- Primary emerald: #35D49A; secondary mint: #9BE8C5; primary container: #123D2D.
- Text: #F0F8F3; muted text: #A7BDB1; warning: #F4C36A; negative: #FF8089.
- Material 3 shapes: cards 24dp, buttons 16dp, chips 12dp.
- Keep light-mode support and existing saved appearance preference.

## Proposed navigation
1. Home: consolidated current balance, safe-to-spend, approval queue, latest activity.
2. Activity: transaction feed, search/filter, review, duplicate management, add transaction.
3. Accounts: MIB/BML/Cash cards, card drilldowns, reconciliation, internal transfers.
4. Insights: treemap, spending trends, merchants, monthly reports, forecasts.
5. More: budgets, savings, financial calendar, imports, privacy, settings, backups.

Provide a floating Add action from Activity and quick access to Import from More.
Preserve the mandatory initial CSV and SMS onboarding flow and back stack.

## Engineering migration plan
- Current MainActivity.kt uses tab indices 0..4 for Home, Transactions, Import, Budgets, Plan.
- Do not simply rename labels: migrate all conditional tab routing and deep links together.
- Extract each destination to a dedicated composable, centralize routes as sealed destinations.
- Extract palette and typography into Theme.kt, use theme colors instead of hardcoded purple.
- Preserve TransactionStore schema, existing financial data, permission flows, biometrics, app package identity.
- Ensure no regressions in reconciliation, balances, approvals, imports, CSV/OCR and merchant icons.
- Test navigation on small devices, back stack, onboarding, and dark/light mode.
- Run Gradle Android assembleDebug and confirm successful APK before merging.

## Acceptance criteria
Every existing feature remains reachable, all destinations show the correct content, and a full clean Android build passes. No financial-data migration or destructive resets.
