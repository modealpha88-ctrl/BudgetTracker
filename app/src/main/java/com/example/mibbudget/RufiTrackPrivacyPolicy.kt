package com.example.mibbudget

/** User-visible policy text. The public HTML copy is in docs/privacy.html. */
internal object RufiTrackPrivacyPolicy {
    val text = """
RufiTrack Privacy Policy · Draft for Play publication (6 November 2026)

RufiTrack is a personal finance and budgeting app. The app processes financial data locally to organize transactions from MIB/BML bank SMS, CSV statements, manually entered transactions, and user-selected screenshots or receipts.

Data processed: financial amounts, merchant and recipient descriptions, transaction dates and times, budget categories, bank balance snapshots, imported statement contents, optional SMS inbox messages, and optional bank notifications. You choose whether to grant SMS or notification access. Only supported transaction records are extracted; unrelated SMS is ignored. Some bank messages may contain personal information. The app does not require your bank login credentials.

Storage: your ledger, imported data, categories and saved settings are kept on your device. Local SQLite storage is not encrypted at rest. Android backup is disabled for the app. You may explicitly share exported reports, backups, or diagnostic summaries with apps you choose; these transfers are controlled by you and the receiving service's privacy practices apply. Feedback sharing is disabled by default and requires your explicit review and action.

Permissions: READ_SMS is used to import historical supported bank transaction messages; RECEIVE_SMS is used for new bank transaction messages. Notification listener access, when enabled, reads supported bank notifications. Biometrics/device credentials protect access when enabled. Image/document pickers are used for files you select. INTERNET is declared for Android libraries, but the current app does not run a developer-controlled financial data upload service.

Retention and deletion: locally saved data remains until you remove or reset it, or uninstall the app. You can delete individual entries and use the in-app data reset/backup tools. Revoking SMS and notification permissions stops future access but does not erase already imported transaction entries. Please create a secure backup before deleting financial data.

This app is independent of MIB and BML and does not provide bank-certified live balances. Future versions or added online services may require this policy to be updated. For privacy questions or correction requests, open an issue in the RufiTrack GitHub repository at github.com/modealpha88-ctrl/BudgetTracker/issues (do not include financial credentials or transaction details).

Developer: RufiTrack project maintainer, modealpha88-ctrl.
    """.trimIndent()
}
