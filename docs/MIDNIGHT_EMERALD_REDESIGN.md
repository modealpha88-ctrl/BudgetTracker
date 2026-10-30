# Midnight Emerald UI and Navigation Redesign

Status: first implementation committed on main. Android CI verification pending. The legacy tool screens remain available as advanced destinations.

## Design tokens
- AMOLED background: #050B09; surface: #101B17; raised surface: #182720.
- Primary emerald: #35D49A; secondary mint: #9BE8C5; primary container: #123D2D.
- Text: #F0F8F3; muted text: #A7BDB1; warning: #F4C36A; negative: #FF8089.
- Material 3 shapes: cards 24dp, buttons 16dp, chips 12dp.
- Keep light-mode support and existing saved appearance preference.

## Implemented navigation
1. Home (route 0): reconciled MIB/BML/Cash account values, monthly income/spending, BML credit alerts, recent activity; Safe-to-spend links to Planning.
2. Activity (route 1): existing transaction feed, search/filter, reviews, duplicate management, add transaction.
3. Accounts (route 5): MIB/BML/Cash cards, dedicated account history/reconciliation, filtered transactions.
4. Insights (route 6): monthly cashflow, treemap spending breakdown, category drilldown; forecasts and reports remain in Planning.
5. More (route 7): navigation to legacy Import (2), Budgets (3), Planning (4), Advanced dashboard (8), with appearance and privacy controls.

Provide a floating Add action from Activity and quick access to Import from More.
Preserve the mandatory initial CSV and SMS onboarding flow and back stack.

## Engineering migration plan
- Original MainActivity.kt used tab indices 0..4 for Home, Transactions, Import, Budgets, Plan; new destinations use 0,1,5,6,7 while the legacy routes remain reachable.
- Do not simply rename labels: migrate all conditional tab routing and deep links together.
- Future refactor: extract all destination bodies from MainActivity.kt to separate composables and centralize routes as sealed destinations. Current migration preserves legacy code paths.
- Implemented: centralized MidnightEmeraldTheme.kt and replaced prominent hardcoded purple/gray colors.
- Preserve TransactionStore schema, existing financial data, permission flows, biometrics, app package identity.
- Ensure no regressions in reconciliation, balances, approvals, imports, CSV/OCR and merchant icons.
- Test navigation on small devices, back stack, onboarding, and dark/light mode.
- GitHub Actions build workflow runs automatically on app source pushes. Verify actual workflow result before installing or claiming a successful APK.

## Acceptance criteria
Target: existing features remain reachable, destinations show correct content, and a full Android build passes. No financial-data migration or destructive resets. Build and device navigation testing are not yet confirmed.
