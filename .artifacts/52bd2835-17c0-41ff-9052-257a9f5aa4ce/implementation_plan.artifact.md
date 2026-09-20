# Fix E2EE Cloud Sync and Database Import Issues

This plan addresses two issues:
1.  **E2EE Cloud Sync**: Plain databases were being uploaded to WebDAV even when E2EE was enabled because the wrong flag was passed to the sync use case.
2.  **Database Import Error**: Importing a database failed with "invalid database file structure!" because the validation logic used an outdated hardcoded database version (49 instead of 51).

## User Review Required

> [!IMPORTANT]
> The remote sync filename will change from `expenses_database_sync.db` to `expenses_database_sync.xpt` when E2EE is enabled. If a user already has a plain database on their WebDAV, they might need to manually delete it or it will coexist with the encrypted one.

## Proposed Changes

### 1. Cloud Sync Logic

#### [MODIFY] [WebDavWorker.kt](file:///home/dock/Downloads/projects/Mob_app/exp-tracker/xpent%20src%20archive/Xpent1.0.63%20no%20image/FinTrack/app/src/main/kotlin/com/openapps/fintrack/data/WebDavWorker.kt)
Update the `syncUseCase` call to pass `encryptRemote` instead of `secureMode`.

#### [MODIFY] [ExpenseViewModel.kt](file:///home/dock/Downloads/projects/Mob_app/exp-tracker/xpent%20src%20archive/Xpent1.0.63%20no%20image/FinTrack/app/src/main/kotlin/com/openapps/fintrack/ui/ExpenseViewModel.kt)
Update `syncNow()` to pass `encryptRemoteEnabled` instead of `secureModeEnabled` to `syncUseCase`.

### 2. Database Import Logic

#### [MODIFY] [DatabaseScreen.kt](file:///home/dock/Downloads/projects/Mob_app/exp-tracker/xpent%20src%20archive/Xpent1.0.63%20no%20image/FinTrack/app/src/main/kotlin/com/openapps/fintrack/ui/DatabaseScreen.kt)
Update `validateDatabaseSchema` to allow database version up to 51 (the current version).

## Verification Plan

### Manual Verification
1.  **E2EE Sync**:
    - Enable E2EE in Database Settings.
    - Perform a "Sync Now".
    - Verify (via logs or checking the WebDAV server) that `expenses_database_sync.xpt` is uploaded and contains encrypted data (starts with `XPT`).
    - Disable E2EE and verify `expenses_database_sync.db` is uploaded as plain SQLite.
2.  **Database Import**:
    - Export a database from the current version of the app.
    - Try to import it using "Open Different Database".
    - Verify it imports successfully without the "invalid database file structure!" error.
