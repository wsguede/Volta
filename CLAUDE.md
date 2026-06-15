# Volta — Claude Code Guide

Volta is a native Android photosphere capture app. Users sweep their phone in a continuous AR-guided motion; the app auto-captures frames, stitches them on-device into an equirectangular JPEG, and exports it to the device photo library with GPS embedded in EXIF. See `PRD.md` for full product requirements.

---

## Architecture

**Pattern:** MVVM with Jetpack
- `ViewModel` + `StateFlow` for all UI state
- Jetpack Compose for all UI
- Unidirectional data flow: UI emits events → ViewModel updates state → UI reacts

**Layers:**
- `ui/` — Composables and ViewModels
- `domain/` — Business logic (capture triggering, coverage tracking, stitching orchestration)
- `data/` — Camera, ARCore, GPS, and file system access

Keep business logic out of Composables. Keep Android framework dependencies out of `domain/`.

---

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose |
| Architecture | ViewModel + StateFlow |
| DI | Hilt |
| Async | Kotlin Coroutines |
| Camera | CameraX |
| AR | ARCore |
| Image Stitching | OpenCV for Android |
| Logging | Timber |
| Navigation | Jetpack Navigation Compose |
| Serialization | Kotlin Serialization |

Do not introduce new dependencies without an ADR.

---

## Testing

- **Unit tests are required** for all business logic: ViewModels, domain classes, and any non-trivial utility.
- Follow **red-green-refactor**: write a failing test first, make it pass, then refactor.
- Instrumentation tests are optional but welcome for critical camera/ARCore paths.
- No enforced coverage percentage — quality over quantity.
- Test files live alongside source in the standard `src/test/` and `src/androidTest/` directories.

---

## Project Structure

```
app/
└── src/
    ├── main/
    │   └── java/com/volta/app/
    │       ├── ui/
    │       │   ├── capture/          # AR capture screen (Composable + ViewModel)
    │       │   ├── export/           # Stitching progress + export screen
    │       │   └── settings/         # Resolution and preferences screen
    │       ├── domain/
    │       │   ├── capture/          # Frame capture triggering, blur detection
    │       │   ├── coverage/         # Sphere coverage tracking
    │       │   └── stitching/        # Stitching orchestration
    │       ├── data/
    │       │   ├── camera/           # CameraX integration
    │       │   ├── ar/               # ARCore integration
    │       │   ├── gps/              # Location / GPS
    │       │   └── export/           # MediaStore / file system
    │       ├── di/                   # Hilt modules
    │       └── VoltaApplication.kt
    ├── test/                         # Unit tests (mirrors main/ structure)
    └── androidTest/                  # Instrumentation tests
docs/
└── adr/                              # Architecture Decision Records
```

Each screen package contains exactly one `*Screen.kt` (Composable) and one `*ViewModel.kt`. No other files belong in `ui/` packages.

---

## Code Style

- **ktlint** enforces formatting. Run `./gradlew ktlintCheck` before committing. CI will fail on violations.
- **Detekt** enforces code quality. Run `./gradlew detekt` before committing. CI will fail on violations.
- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Use `Timber` for all logging — never use `android.util.Log` directly.
- No `TAG` constants — Timber infers the tag automatically.

**Naming conventions:**
- Composables: `PascalCase`, noun or noun-phrase (e.g., `CaptureScreen`, `SphereOverlay`).
- ViewModels: `PascalCase` suffixed with `ViewModel` (e.g., `CaptureViewModel`).
- StateFlow UI state: a sealed class or data class suffixed with `UiState` (e.g., `CaptureUiState`).
- Repository/data classes: suffixed with `Repository` or `DataSource` (e.g., `CameraRepository`).
- Hilt modules: suffixed with `Module` (e.g., `CameraModule`).

**Coroutines:**
- Launch coroutines from `viewModelScope` inside ViewModels.
- Use `Dispatchers.IO` for file and sensor I/O; `Dispatchers.Default` for CPU-bound work (stitching, blur detection).
- Never hardcode `Dispatchers` — inject them so they can be replaced in tests.

**Compose:**
- Composables must be stateless where possible — hoist state to the ViewModel.
- Pass lambdas for events, not ViewModel references, into Composables.
- Prefix preview functions with `Preview` and annotate with `@Preview`.

---

## Architecture Decision Records (ADRs)

Any notable architecture decision must be documented as an ADR.

- Location: `docs/adr/`
- Format: `docs/adr/NNNN-short-title.md` (e.g., `docs/adr/0001-use-opencv-for-stitching.md`)
- Template:

```markdown
# NNNN — Decision Title

**Date:** YYYY-MM-DD  
**Status:** Accepted | Deprecated | Superseded by NNNN

## Context
What problem or situation prompted this decision?

## Decision
What was decided?

## Consequences
What are the trade-offs, risks, or follow-on work?
```

When in doubt about whether a decision warrants an ADR, write one. Examples that always warrant an ADR: adding a new dependency, changing the architecture pattern, choosing between two significant implementation approaches.

---

## Git Workflow

**Strategy:** GitHub Flow
- `main` is always stable and releasable.
- All work happens on short-lived feature branches cut from `main`.
- Branch naming: `feature/short-description`, `fix/short-description`, `chore/short-description`.
- Merge via pull request with at least one review.
- Delete branches after merging.

**Versioning:** CalVer — `YYYY.MM.MICRO`
- Example: `2026.06.1` (first release in June 2026), `2026.06.2` (second release same month).
- Tag releases on `main`: `git tag v2026.06.1`.
- Android `versionCode` is an incrementing integer independent of CalVer.

---

## Platform

- **Language:** Kotlin only — no Java.
- **Minimum SDK:** N-1 from the latest major Android release at time of development.
- **Distribution:** Sideloaded APK (no Play Store for v1).
- **Build system:** Gradle with Kotlin DSL (`build.gradle.kts`).

---

## Key Constraints

- All processing (stitching, frame analysis) must work fully offline — no network calls in the capture or export pipeline.
- GPS coordinates are optional — the app warns but never blocks if location is unavailable.
- The app is session-focused: no persistent storage of photospheres. Once exported to the photo library, the app retains nothing.
- Output format is equirectangular JPEG with `GPano` XMP metadata so Google Maps recognises it as a photosphere.
- Output resolution is user-configurable: minimum 4096×2048, default 8192×4096.
