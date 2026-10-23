# MIB Budget Tracker — monthly budgets + one-month inbox import

## Features
- Monthly dashboard and category budget progress, using the SMS transaction date, with previous/next month navigation.
- Conservative keyword categorization: restaurants/food, groceries (including FIHAARA), fuel/transport, utilities/bills, health. Unknown merchants stay Other. Favara outgoing transfers stay Transfers; transfers are counted as outflow, which may double-count transfers between your own accounts.
- **Explicit, user-triggered** import of inbox messages dated from one calendar month before today through now. The app requests `READ_SMS` at runtime and scans only SMS inbox entries in that date range, up to 10,000 messages. It parses only the two sample MIB debit formats, stores extracted transaction fields (not raw messages), and ignores unsupported messages. Re-import is deduplicated by normalized SMS body hash. Note: identical SMS content can still lead to false deduplication, and different wording can cause missed duplicates.
- New incoming SMS automatic capture remains opt-in through `RECEIVE_SMS`.
- Offline local SQLite storage, manual entry, manual SMS paste/share, category budgets.

## Build and run
1. Open this folder in Android Studio with JDK 17 and Android SDK 35; allow Gradle sync.
2. Connect Android 8+ device with USB debugging or use an SMS-capable emulator. Click Run.
3. On Dashboard tap **Import previous 1 month of SMS**, grant `READ_SMS`, and wait for the summary. Enable automatic new SMS detection separately if desired.
4. Inspect the results against the official MIB app or statement. Set category budgets and browse months.

## Important limitations
- Android `READ_SMS` and `RECEIVE_SMS` are sensitive restricted permissions. Google Play generally restricts SMS permissions to approved use cases such as default SMS handlers or eligible exceptions; **do not assume this personal budget app is eligible for Play distribution**. Sideloaded builds may also face restricted-settings or other device limitations. Manual import remains an alternative.
- No verified sender authentication: SMS can be spoofed. Sample-based parser is not guaranteed to recognize all MIB formats, credits, reversals, or fees. A one-month SMS scan is **not** an accurate or complete bank ledger; compare against official statements.
- Inbox import uses device-local dates for its cutoff. Transaction date is parsed from SMS text. No raw SMS bodies or full account/card numbers are persisted by the parser, but descriptions and timestamps are sensitive financial data. **SQLite is not encrypted at rest.** No INTERNET permission or remote transfer is configured.
- History import is one-time per tap; it does not silently read the inbox. Revoking SMS permissions in Android settings disables future access.
- Build not compiled or device-tested here. Test parser, permissions, and monthly totals before relying on the results.
