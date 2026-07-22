# 0011 — Use DataStore Preferences for Settings Persistence

**Date:** 2026-07-22
**Status:** Accepted

## Context

Issue #15 requires the user's output resolution selection to persist across app restarts. Android offers two mainstream options for small key-value persistence:
1. `SharedPreferences` — legacy, synchronous API, no coroutine/Flow support, prone to main-thread I/O footguns.
2. Jetpack `DataStore<Preferences>` — the modern replacement for `SharedPreferences`, asynchronous, `Flow`-based, transactional writes via `edit {}`.

Volta's architecture is coroutine/`Flow`-first throughout (`StateFlow` in ViewModels, `Flow` in repositories per [0005](0005-use-kotlin-coroutines.md)), so the persistence layer should expose the same shape.

## Decision

Use `androidx.datastore:datastore-preferences` for settings persistence. A `SettingsRepository` interface lives in `data/settings/`, exposing `outputResolution: Flow<OutputResolution>` and a `suspend fun setOutputResolution(...)`. `DataStoreSettingsRepository` implements it against an injected `DataStore<Preferences>`, provided as a singleton by `di/SettingsModule.kt`.

## Consequences

- `SettingsViewModel.uiState` derives directly from the repository's `Flow` via `stateIn`, keeping DataStore as the single source of truth rather than duplicating state in the ViewModel.
- Reads and writes are off the main thread by default, avoiding the disk I/O jank `SharedPreferences.apply()`/`commit()` can cause.
- Adds one dependency (`datastore-preferences`) to `gradle/libs.versions.toml`; no other persistence mechanism is introduced.
- `PreferenceDataStoreFactory.create` (used directly, without `Context.dataStore` property delegation) keeps the domain-facing `OutputResolution` enum free of any DataStore/Android import — only `data/settings/` touches the DataStore APIs.
- Only one `OutputResolution` preference is stored today; if settings grow, this same `DataStore<Preferences>` instance can hold additional keys without further ADRs.
