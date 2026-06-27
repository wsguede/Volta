# Volta — Agent Guide

Volta is built agent-first. We design systems and use agents to implement them.

Volta is a native Android photosphere capture app. Users sweep their phone in a continuous AR-guided motion; the app auto-captures frames, stitches them on-device into an equirectangular JPEG, and exports it to the device photo library with GPS embedded in EXIF.

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
.agents/
├── skills/                           # Shared agent skills (harness-agnostic)
└── agents/                           # Sub-agent persona definitions
docs/
└── adr/                              # Architecture Decision Records
```

Each screen package contains exactly one `*Screen.kt` (Composable) and one `*ViewModel.kt`.

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

**Formatting:**
- ktlint enforces formatting
- Detekt enforces code quality
- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)

---

## Testing

- Unit tests are required for all business logic: ViewModels, domain classes, non-trivial utilities
- Follow red-green-refactor: write a failing test first, make it pass, then refactor
- Instrumentation tests are optional but welcome for critical camera/ARCore paths
- Test files live in `src/test/` and `src/androidTest/` mirroring `main/` structure

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
- Merge via pull request
- Delete branches after merging

**Versioning:** CalVer — `YYYY.MM.MICRO` (e.g., `2026.06.1`).

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
