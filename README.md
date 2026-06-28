# Volta

[![CI](https://github.com/wsguede/Volta/actions/workflows/ci.yml/badge.svg)](https://github.com/wsguede/Volta/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-11%2B%20(API%2030)-green.svg)](https://developer.android.com/about/versions/11)
[![ARCore](https://img.shields.io/badge/ARCore-Required-orange.svg)](https://developers.google.com/ar/devices)

**Capture complete 360° photospheres with AR guidance — entirely on your phone.**

Volta is a native Android app that guides you through capturing a full photosphere using real-time AR overlays. Sweep your phone in any direction — the app auto-captures frames when conditions are right, stitches them on-device into an equirectangular JPEG, and saves the result to your photo library with GPS coordinates embedded in EXIF. Ready for upload to Google Maps, Street View Studio, or any compatible platform.

> **Status: Early Development** — Volta is under active development and not yet feature-complete. Expect rough edges. See the [PRD](https://github.com/wsguede/Volta/issues/1) for the full product spec.

---

## Features

- **AR-Guided Capture** — Real-time 3D sphere overlay shows exactly where you've captured and where gaps remain
- **Smart Auto-Capture** — Frames are taken automatically when angular spacing and sharpness thresholds are both met
- **On-Device Stitching** — All processing happens locally using OpenCV; no cloud, no network, no data leaves your phone
- **GPS & Metadata** — Coordinates and `GPano` XMP metadata are embedded so platforms recognize the output as a photosphere
- **Session-Focused** — No accounts, no gallery, no persistent storage; capture, export, done
- **Configurable Resolution** — Choose between Minimum (4096 × 2048) and Standard (8192 × 4096) output

---

## Requirements

### Device

| Requirement | Details |
|---|---|
| Android | 11 or higher (API 30+) |
| ARCore | Required — [check device compatibility](https://developers.google.com/ar/devices) |
| Camera | Rear-facing camera |
| GPS | Optional — used for EXIF metadata if available |

### Build Environment

| Tool | Version |
|---|---|
| JDK | 17 |
| Gradle | 9.6.1 (via included wrapper) |
| Android SDK | API 35 (compile/target) |
| Kotlin | 2.0.21 |

---

## Getting Started

### Clone and Build

```bash
git clone https://github.com/wsguede/Volta.git
cd Volta
```

Build a debug APK:

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

Build a release APK:

```bash
./gradlew assembleRelease
```

### Run Tests

```bash
# Unit tests
./gradlew testDebugUnitTest

# Code quality
./gradlew ktlintCheck detekt lint
```

### Open in Android Studio

1. Open Android Studio
2. Select **File → Open** and choose the `Volta/` directory
3. Wait for Gradle sync to complete
4. Select a device or emulator and click **Run**

> **Note:** ARCore features require a physical device — emulators do not support ARCore sensor fusion.

---

## Installing the APK

Volta is distributed as a sideloaded APK (no Play Store for v1).

### From a Release

1. Go to [Releases](https://github.com/wsguede/Volta/releases) and download the latest `.apk` file
2. Transfer the APK to your Android device (USB, cloud drive, email, etc.)
3. On your device, open the APK file
4. If prompted, enable **Install from unknown sources** for the app you're using to open the file (Files, Chrome, etc.)
5. Tap **Install**

### From ADB

If you have the Android SDK platform tools installed:

```bash
# List connected devices
adb devices

# Install the APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Or install a release build
adb install app/build/outputs/apk/release/app-release.apk
```

### Permissions

On first launch, Volta will request:

| Permission | Why |
|---|---|
| **Camera** | Live viewfinder and frame capture |
| **Location** | GPS coordinates for EXIF metadata (optional — you can deny and still use the app) |

---

## Architecture

Volta follows **MVVM with Jetpack Compose** and unidirectional data flow.

```
┌──────────────────────────────────────────────┐
│  UI Layer (Compose)                          │
│  CaptureScreen · ExportScreen · Settings     │
├──────────────────────────────────────────────┤
│  ViewModels (StateFlow)                      │
│  CaptureViewModel · ExportViewModel · ...    │
├──────────────────────────────────────────────┤
│  Domain Layer (pure Kotlin)                  │
│  BlurDetector · CoverageTracker · Stitching  │
├──────────────────────────────────────────────┤
│  Data Layer (Android/SDK)                    │
│  CameraX · ARCore · GPS · MediaStore         │
└──────────────────────────────────────────────┘
```

| Layer | Responsibility | Rule |
|---|---|---|
| `ui/` | Composables and ViewModels | May depend on `domain/` and Android framework |
| `domain/` | Business logic | **No Android imports** — pure Kotlin only |
| `data/` | Hardware and OS access | Camera, AR, GPS, file system |
| `di/` | Hilt dependency injection | Wires all layers together |

See [`docs/adr/`](docs/adr/) for architecture decision records explaining key choices (MVVM, Hilt, CameraX, OpenCV, Coroutines, Android-first).

---

## Project Structure

```
app/src/main/java/com/volta/app/
├── ui/
│   ├── capture/       # AR capture screen
│   ├── export/        # Stitching progress + export
│   ├── settings/      # Resolution preferences
│   └── theme/         # Material 3 dark theme
├── domain/
│   ├── capture/       # Frame analysis, blur detection
│   ├── coverage/      # Sphere coverage tracking
│   └── stitching/     # Stitching orchestration
├── data/
│   ├── camera/        # CameraX integration
│   ├── ar/            # ARCore session management
│   ├── gps/           # Location services
│   └── export/        # MediaStore / file system
├── di/                # Hilt modules
├── navigation/        # Nav graph
├── MainActivity.kt
└── VoltaApplication.kt
```

---

## CI/CD

Every push and pull request runs:

| Job | What it checks |
|---|---|
| **Code Quality** | ktlint formatting + Detekt static analysis |
| **Android Lint** | Android-specific lint rules |
| **Unit Tests** | All `testDebugUnitTest` tests |
| **Build** | Assembles debug APK (uploaded as artifact) |

Pushes to `main` additionally trigger a **release workflow** that:
1. Computes a [CalVer](https://calver.org/) version (`YYYY.M.MICRO`)
2. Builds a release APK
3. Generates a changelog grouped by conventional commit type
4. Creates a GitHub Release with the APK attached

---

## Contributing

1. Check [open issues](https://github.com/wsguede/Volta/issues) for planned work
2. Branch from `main` using the naming convention: `feature/short-description`, `fix/short-description`, or `chore/short-description`
3. Follow [conventional commits](https://www.conventionalcommits.org/) scoped by layer (e.g., `feat(capture): add blur detection`)
4. Ensure `./gradlew ktlintCheck detekt testDebugUnitTest` passes before opening a PR
5. Write an [ADR](docs/adr/TEMPLATE.md) if your change introduces a dependency or makes an architecture decision

See [AGENTS.md](AGENTS.md) for full code style, naming conventions, and project workflows.

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + StateFlow |
| DI | Hilt (KSP) |
| Camera | CameraX |
| AR | ARCore |
| Stitching | OpenCV (planned) |
| Async | Kotlin Coroutines + Flow |
| Logging | Timber |
| CI | GitHub Actions |
| Versioning | CalVer (`YYYY.MM.MICRO`) |

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
