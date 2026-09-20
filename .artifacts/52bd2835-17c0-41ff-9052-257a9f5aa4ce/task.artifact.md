# Phase 6 Tasks: Sync & Security

- [x] Harden Encryption Service
    - [x] Implement `rotatePassword`
    - [x] Add configurable iterations support
- [x] Improve Local Server Security
    - [x] Persist JWT Secret in EncryptedPrefs
    - [x] Implement rate limiting for pairing
    - [x] Implement HTTPS support (Ready for SSL)
- [x] Implement Bi-directional Sync
    - [x] Add `isDeleted` and ensure `editedAt` for all entities (Migration 44 -> 45)
    - [x] Create `SyncUseCase` with Merge Logic
    - [x] Update `WebDavWorker` to use `SyncUseCase`
- [x] Sync UI Improvements
    - [x] Add "Sync Now" trigger
    - [x] Display detailed sync status
- [x] Verification & Tests (Build passed, Manifest conflict resolved)
