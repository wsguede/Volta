# Volta — Agent Guide

This file is the primary instruction surface for agents contributing to Volta. It is injected into your context on every interaction — keep that in mind when proposing changes to it.

Volta is built agent-first. We design systems and use agents to implement them.

Volta is a native Android photosphere capture app. Users sweep their phone in a continuous AR-guided motion; the app auto-captures frames, stitches them on-device into an equirectangular JPEG, and exports it to the device photo library with GPS embedded in EXIF.

---

## Essential Commands

### Building

```bash
./gradlew assembleDebug          # Debug APK → app/build/outputs/apk/debug/
./gradlew assembleRelease        # Release APK → app/build/outputs/apk/release/
```

### Testing

```bash
./gradlew testDebugUnitTest      # Unit tests
```

### Code Quality

```bash
./gradlew ktlintCheck            # Formatting (ktlint)
./gradlew detekt                 # Static analysis (Detekt)
./gradlew lint                   # Android lint
```

### Full Pre-Commit Check

Run all quality gates before committing:

```bash
./gradlew ktlintCheck detekt lint testDebugUnitTest
```

### Installing on a Device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

> **Note:** ARCore features require a physical device — emulators do not support ARCore sensor fusion.

---

## Build Environment

| Tool | Version | Notes |
|---|---|---|
| JDK | 17 | CI uses Temurin; local JDK version may differ |
| Gradle | 9.6.1 | Via included wrapper (`./gradlew`) — always use the wrapper |
| Android SDK | API 35 | `compileSdk` and `targetSdk` |
| Kotlin | 2.0.21 | With Compose compiler plugin |
| Min Android | API 30 | Android 11+ |

Dependencies are managed via the Gradle version catalog at `gradle/libs.versions.toml`. Do not add dependency versions directly in `build.gradle.kts` — add them to the catalog.

---

## CI/CD Pipeline

CI runs on every push and pull request to `main` via GitHub Actions (`.github/workflows/ci.yml`).

| Job | What it runs | Depends on |
|---|---|---|
| **Code Quality** | `ktlintCheck` + `detekt` | — |
| **Android Lint** | `lint` | — |
| **Unit Tests** | `testDebugUnitTest` | — |
| **Build** | `assembleDebug` (APK uploaded as artifact) | All three above |

### Release Pipeline

Pushes to `main` also trigger `.github/workflows/release.yml`:

1. Computes a CalVer version tag (`vYYYY.M.MICRO`) by counting existing tags for the current month
2. Builds a release APK
3. Generates a changelog grouped by conventional commit type (features, fixes, refactoring, docs, tests, maintenance)
4. Creates a GitHub Release with the APK attached

The release workflow is idempotent — if the computed tag already exists, it skips all steps.

### Versioning

CalVer format: `YYYY.M.MICRO` (e.g., `2026.6.0`, `2026.6.1`).

- `YYYY.M` is derived from the current date at build time
- `MICRO` increments per release within the same month
- `versionCode` is computed as `YYYY * 1000 + M * 10` for Play Store compatibility
- Pass `-PVERSION_MICRO=N` to override the micro version in release builds

---

## Architecture

**Pattern:** MVVM with Jetpack — unidirectional data flow.

- `ViewModel` + `StateFlow` for all UI state
- Jetpack Compose for all UI
- UI emits events → ViewModel updates state → UI reacts

**Layers:**

| Layer | Responsibility | Allowed dependencies |
|---|---|---|
| `ui/` | Composables and ViewModels | `domain/`, Android framework |
| `domain/` | Business logic (capture, coverage, stitching) | Kotlin stdlib only — no Android imports |
| `data/` | Camera, ARCore, GPS, file system access | Android framework, external SDKs |
| `di/` | Hilt modules | All layers |

Keep business logic out of Composables. Keep Android framework dependencies out of `domain/`.

Architecture decisions are documented in `docs/adr/`. See the [ADR section](#architecture-decision-records) below.

---

## Project Structure

```
app/
└── src/
    ├── main/
    │   └── java/com/volta/app/
    │       ├── ui/
    │       │   ├── capture/          # AR capture screen
    │       │   ├── export/           # Stitching progress + export screen
    │       │   ├── settings/         # Resolution and preferences screen
    │       │   └── theme/            # Material 3 dark theme
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
    │       ├── navigation/           # Nav graph
    │       └── VoltaApplication.kt
    ├── test/                         # Unit tests (mirrors main/ structure)
    └── androidTest/                  # Instrumentation tests
.agents/
├── skills/                           # Shared agent skills (harness-agnostic)
└── agents/                           # Sub-agent persona definitions
.github/
├── workflows/
│   ├── ci.yml                        # CI pipeline (lint, test, build)
│   └── release.yml                   # Release pipeline (APK + changelog)
├── ISSUE_TEMPLATE/                   # Bug report and feature request templates
└── PULL_REQUEST_TEMPLATE.md          # PR template with checklist
config/
└── detekt/
    └── detekt.yml                    # Detekt static analysis config
docs/
└── adr/                              # Architecture Decision Records
gradle/
├── libs.versions.toml                # Version catalog (single source for all dependency versions)
└── wrapper/                          # Gradle wrapper (always use ./gradlew)
```

Each screen package contains exactly one `*Screen.kt` (Composable), one `*ViewModel.kt`, and one `*UiState.kt`.

---

## Technology Stack

| Component | Technology | ADR |
|---|---|---|
| Language | Kotlin | [0006](docs/adr/0006-android-first.md) |
| UI | Jetpack Compose + Material 3 | [0001](docs/adr/0001-use-mvvm-architecture.md) |
| Architecture | MVVM + StateFlow | [0001](docs/adr/0001-use-mvvm-architecture.md) |
| DI | Hilt (KSP) | [0002](docs/adr/0002-use-hilt-for-dependency-injection.md) |
| Camera | CameraX | [0003](docs/adr/0003-use-camerax.md) |
| AR | ARCore | [0006](docs/adr/0006-android-first.md) |
| Stitching | OpenCV (planned) | [0004](docs/adr/0004-use-opencv-for-stitching.md) |
| Async | Kotlin Coroutines + Flow | [0005](docs/adr/0005-use-kotlin-coroutines.md) |
| Logging | Timber | — |
| Formatting | ktlint | — |
| Static analysis | Detekt | — |
| CI | GitHub Actions | — |
| Versioning | CalVer (`YYYY.M.MICRO`) | — |

---

## Code Style

**Naming conventions:**

| Element | Convention | Example |
|---|---|---|
| Composables | PascalCase, noun/noun-phrase | `CaptureScreen`, `SphereOverlay` |
| ViewModels | PascalCase + `ViewModel` suffix | `CaptureViewModel` |
| UI state | Sealed/data class + `UiState` suffix | `CaptureUiState` |
| Repositories | `Repository` or `DataSource` suffix | `CameraRepository` |
| Hilt modules | `Module` suffix | `CameraModule` |

**Compose rules:**
- Composables must be stateless where possible — hoist state to the ViewModel
- Pass lambdas for events, not ViewModel references, into Composables
- Prefix preview functions with `Preview` and annotate with `@Preview`

**Coroutine rules:**
- Launch coroutines from `viewModelScope` inside ViewModels
- Use `Dispatchers.IO` for file and sensor I/O; `Dispatchers.Default` for CPU-bound work
- Never hardcode `Dispatchers` — inject them so they can be replaced in tests

**Logging:**
- Use `Timber` for all logging — never `android.util.Log` directly
- No `TAG` constants — Timber infers the tag

**Formatting and analysis:**
- ktlint enforces formatting — run `./gradlew ktlintCheck` (auto-fix with `./gradlew ktlintFormat`)
- Detekt enforces code quality — config at `config/detekt/detekt.yml`
- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)

---

## Testing

- Unit tests are required for all business logic: ViewModels, domain classes, non-trivial utilities
- Follow red-green-refactor: write a failing test first, make it pass, then refactor
- Instrumentation tests are optional but welcome for critical camera/ARCore paths
- Test files live in `src/test/` and `src/androidTest/` mirroring `main/` structure
- Use Truth for assertions, MockK for mocking, Turbine for Flow testing
- Always run `./gradlew testDebugUnitTest` before committing

---

## Architecture Decision Records

Any notable architecture decision must be documented as an ADR.

- Location: `docs/adr/`
- Format: `docs/adr/NNNN-short-title.md`
- Template: `docs/adr/TEMPLATE.md`

When in doubt, write one. Always write an ADR when: adding a dependency, changing the architecture pattern, or choosing between two significant approaches.

---

## Commit Conventions

Use conventional commits scoped by layer:

```
feat(capture): add blur detection to frame analysis
fix(stitching): correct equirectangular projection at poles
refactor(data): extract GPS permission flow from CameraRepository
test(coverage): add CoverageTracker unit tests
chore(ci): add ktlint check to CI pipeline
docs(adr): add ADR for OpenCV stitching choice
```

Valid scopes: `capture`, `coverage`, `stitching`, `camera`, `ar`, `gps`, `export`, `ui`, `di`, `ci`, `adr`, `infra`.

---

## Git Workflow

**Strategy:** GitHub Flow

- `main` is always stable and releasable
- All work on short-lived feature branches: `feature/short-description`, `fix/short-description`, `chore/short-description`
- Merge via pull request — use the PR template (`.github/PULL_REQUEST_TEMPLATE.md`)
- Delete branches after merging
- Every push to `main` triggers a release — see [Release Pipeline](#release-pipeline)

**Versioning:** CalVer — `YYYY.M.MICRO` (e.g., `2026.6.1`).

---

## GitHub Issue Workflow

Issues are the source of truth for all planned work. Labels serve as a machine-readable contract between humans and agents.

### Label Taxonomy

**Type labels:**

| Label | Description |
|---|---|
| `type:prd` | Product Requirements Document — high-level feature spec that spawns child issues |
| `type:feature` | Feature request — a discrete, implementable unit of work |
| `type:bug` | Bug report |
| `type:chore` | Maintenance, infrastructure, tooling |

**State labels (workflow contract):**

| Label | Meaning | Who transitions |
|---|---|---|
| `state:triage` | Needs assessment and scoping | Human or agent on creation |
| `state:ready` | Requirements clear, ready to implement | Human after review |
| `state:in-progress` | Actively being worked on | Agent on start |
| `state:review` | PR opened, under review | Agent on PR creation |

**Layer labels:**

| Label | Scope |
|---|---|
| `layer:ui` | Composables, ViewModels, theme |
| `layer:domain` | Business logic — capture, coverage, stitching |
| `layer:data` | Camera, AR, GPS, export repositories |
| `layer:infra` | CI, build config, tooling, agent infrastructure |

**Priority labels:** `priority:high`, `priority:medium`, `priority:low`

### PRD Workflow

PRDs are GitHub issues labeled `type:prd`. They describe a high-level product feature and spawn child `type:feature` issues for implementation.

```
PRD (type:prd) → Feature issues (type:feature, state:triage)
  → state:ready → state:in-progress → state:review → closed
```

Agents should read the PRD issue for context before implementing any child feature.

### Workflow Chains

```
Feature flow:  issue → plan → implement (TDD) → build-and-verify → create-pr
Bug fix flow:  issue → debug → fix → build-and-verify → create-pr
Architecture:  decision → write-adr → review (architect persona) → implement
```

---

## Agent Skills

Skills live in `.agents/skills/`. Each skill is a directory with a `SKILL.md` that defines the skill's purpose, when to invoke it, and how it works. Always read the `SKILL.md` before invoking.

---

## Agent Personas

Persona definitions live in `.agents/agents/`. Use these when you need a specialized review perspective.

---

## Key Constraints

- All processing (stitching, frame analysis) must work fully offline — no network calls in capture or export
- GPS coordinates are optional — warn but never block if location is unavailable
- Session-focused: no persistent storage of photospheres — once exported, the app retains nothing
- Output format: equirectangular JPEG with `GPano` XMP metadata
- Do not introduce new dependencies without an ADR
- Always use the Gradle wrapper (`./gradlew`) — never bare `gradle`
- Never commit secrets, API keys, or keystore files
