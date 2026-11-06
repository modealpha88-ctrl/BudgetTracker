# Restricted SMS permission — reviewer explanation (draft)

App: RufiTrack
Use case: SMS-based money management / personal budgeting
Permissions: android.permission.READ_SMS and android.permission.RECEIVE_SMS

## Why each permission is requested
- READ_SMS: With explicit Android permission and an app-side import action, scan supported historical MIB/BML bank transaction alerts so the user can reconcile an existing budget ledger.
- RECEIVE_SMS: With explicit opt-in, recognize new supported bank transaction alerts and update the locally stored transaction review/import flow.

The app is not a messaging client. It ignores unsupported messages and does not provide SMS sending or message forwarding. CSV statement import and manual transaction entry remain available without SMS access. The user may revoke Android permissions and disable automatic import.

## Reviewer demonstration
1. Launch RufiTrack and enter the read-only demonstration mode to inspect budgeting functionality without supplying banking information.
2. Exit demo; in the normal setup flow choose a CSV statement, or proceed through the supported optional SMS import flow when appropriate.
3. Open the Import & Access area and show the SMS permission explanation and enable/disable controls.
4. On a test device containing supported bank transaction alerts, show historical import and automatic capture, followed by the transaction review screen.
5. Show how imported transactions feed budgets and spending insights.

## Policy considerations
Google Play must determine whether this implementation qualifies for the SMS-based money management exception. Never claim prior approval. Confirm the exact current Google Play policy and declaration form at submission time.

Do not submit real SMS, screenshots containing personal banking details, or account numbers as reviewer evidence. Use a controlled test account/device or redacted demo material. The reviewer video and permission form are manual submission steps.
