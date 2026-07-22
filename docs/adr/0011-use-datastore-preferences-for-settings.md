# 0011 — Use DataStore Preferences for Settings Persistence

**Date:** 2026-07-21
**Status:** Accepted

## Context

Issue #15 requires the user's output resolution selection to persist across app restarts. Android offers two mainstream options for small key-value persistence:
1. `SharedPreferences` — legacy, synchronous API, no coroutine/Flow support, prone to main-thread I/O footguns.
2. Jetpack `DataStore<Preferences>` — the modern replacement for `SharedPreferences`, asynchronous, `Flow`-based, transactional writes via `edit {}`.

Volta's architecture is coroutine/`Flow`-first throughout (`StateFlow` in ViewModels, `Flow` in repositories per [0005](0005-use-kotlin-coroutines.md)), so the persistence layer should expose the same shape.

## Decision

Use `androidx.datastore:datastore-preferences` for settings persistence. Per AGENTS.md's layer rules (`ui/` may depend only on `domain/`), the `SettingsRepository` port — `outputResolution: Flow<OutputResolution>` and `suspend fun setOutputResolution(...)` — lives in `domain/settings/` and has zero Android imports. `DataStoreSettingsRepository` in `data/settings/` is the adapter: it implements the port against an injected `DataStore<Preferences>`, provided as a singleton by `di/SettingsModule.kt`, which binds the adapter to the port.

## Consequences

- `SettingsViewModel.uiState` derives directly from the repository's `Flow` via `stateIn`, keeping DataStore as the single source of truth rather than duplicating state in the ViewModel.
- `SettingsViewModel` depends only on `domain/settings/SettingsRepository`, so it stays compliant with AGENTS.md's `ui/` → `domain/` dependency rule without a table exception.
- Reads and writes are off the main thread by default, avoiding the disk I/O jank `SharedPreferences.apply()`/`commit()` can cause.
- Adds one dependency (`datastore-preferences`) to `gradle/libs.versions.toml`; no other persistence mechanism is introduced.
- `PreferenceDataStoreFactory.create` (used directly, without `Context.dataStore` property delegation) keeps all DataStore/Android imports confined to `data/settings/`.
- Only one `OutputResolution` preference is stored today; if settings grow, this same `DataStore<Preferences>` instance can hold additional keys without further ADRs.
