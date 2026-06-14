# Volta — Product Requirements Document

**Version:** 1.0  
**Status:** Draft  
**Last Updated:** 2026-06-14

---

## 1. Overview

Volta is a native Android app that guides users in capturing a complete 360° photosphere using their phone camera. The finished photosphere is exported as an equirectangular JPEG to the device photo library, ready for upload to Google Maps, Street View Studio, or any compatible platform.

---

## 2. Goals

- Enable any Android user to capture a high-quality, complete photosphere without specialized hardware.
- Provide real-time AR guidance so users know exactly where they still need to point their camera.
- Produce a standards-compliant equirectangular JPEG with GPS coordinates embedded in EXIF metadata.
- Keep the experience session-focused and friction-free — no accounts, no cloud backend, no gallery to manage.

---

## 3. Non-Goals (v1)

- iOS support (planned for a future version).
- Direct upload to Google Maps or Street View API.
- In-app photosphere gallery or history.
- User onboarding or tutorial flow.
- Monetization.
- Google Play Store distribution (sideloaded APK only for v1).

---

## 4. Target Platform

| Attribute | Value |
|---|---|
| Platform | Android native |
| Language | Kotlin |
| AR Framework | ARCore |
| Minimum Android Version | N-1 from the latest major Android release at time of development |
| Distribution | Sideloaded APK |
| License | Open source |

---

## 5. Core User Flow

1. User opens Volta.
2. App acquires GPS coordinates (or warns user if GPS is unavailable/inaccurate).
3. User begins a capture session — live camera feed opens with a 3D sphere AR overlay.
4. User sweeps their phone in all directions. Captured areas of the sphere become clear; uncaptured areas remain greyed out.
5. The app automatically captures frames when two conditions are both met:
   - The phone has rotated far enough from the position of the last captured frame (angular spacing threshold).
   - The current camera frame is sharp enough (blur detection threshold).
6. When the user is satisfied with coverage, they tap **Export**.
   - If coverage is below the recommended threshold, a warning is shown before proceeding.
7. The app stitches all captured frames on-device into an equirectangular JPEG.
8. The finished image (with GPS embedded in EXIF) is saved to the device photo library.
9. Session ends. No data is retained by the app.

---

## 6. Feature Requirements

### 6.1 AR Capture View

- The live camera feed occupies the full screen.
- A real-time 3D sphere overlay is rendered on top of the camera feed using ARCore and OpenGL/Vulkan.
- Sphere sections are **greyed out** until a valid frame has been captured for that region.
- Greyed-out sections become **clear** as they are captured, giving the user unambiguous visual feedback.
- The sphere overlay tracks phone orientation via ARCore's sensor fusion (gyroscope + accelerometer + visual odometry), minimising orientation drift.

### 6.2 Automatic Frame Capture

Frames are captured automatically when **both** of the following conditions are satisfied simultaneously:

| Condition | Description |
|---|---|
| Angular spacing | Phone has rotated a minimum angular distance from the position of the last captured frame |
| Blur detection | Current frame sharpness meets a minimum threshold (low motion blur) |

Specific threshold values (degrees, sharpness score) are implementation details to be tuned during development.

### 6.3 GPS / Location

- The app requests location permission on launch.
- If GPS is available, coordinates are silently acquired and embedded in the exported JPEG's EXIF metadata.
- If GPS is unavailable or below an acceptable accuracy threshold, the app displays a **non-blocking warning** (e.g., a banner or dialog) and allows the user to proceed without coordinates.
- No manual coordinate entry in v1.

### 6.4 Coverage Tracking & Export Warning

- The app tracks what percentage of the full sphere (360° × 180°) has been captured.
- Before export, if coverage is below the recommended threshold, the app presents a warning:
  > *"Your photosphere has uncovered areas. The exported image may be rejected by Google Maps. Continue anyway?"*
- The user may dismiss the warning and export regardless.
- The export button is always enabled; coverage is advisory only.

### 6.5 On-Device Stitching

- All captured frames are stitched on-device into a single equirectangular (2:1 aspect ratio) JPEG.
- Stitching algorithm and library selection are implementation details.
- Stitching must complete without requiring a network connection.
- The user sees a progress indicator during stitching.

### 6.6 Output Resolution

| Setting | Resolution | Approx. Megapixels |
|---|---|---|
| Minimum | 4096 × 2048 | ~8 MP |
| Standard (default) | 8192 × 4096 | ~33 MP |

- The user can select output resolution in app settings.
- The default is **Standard (8192 × 4096)**.

> **Action Item:** Verify Google Street View's current maximum accepted file size and resolution before finalising the export specification. Adjust the upper bound of the resolution setting accordingly.

### 6.7 Export to Photo Library

- The finished equirectangular JPEG is saved directly to the device's photo library (MediaStore).
- The app requests the appropriate storage/media permissions.
- GPS coordinates and the `GPano` XMP metadata namespace (required for Google Maps to recognise the image as a photosphere) are embedded in the file.
- No copy of the image is retained by the app after export.

---

## 7. Permissions Required

| Permission | Purpose |
|---|---|
| `CAMERA` | Live camera feed for capture |
| `ACCESS_FINE_LOCATION` | GPS coordinates for EXIF metadata |
| `WRITE_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` | Saving the exported JPEG to the photo library |

---

## 8. Out-of-Scope Technical Decisions (v1)

The following are left to implementation discretion and are not prescribed by this PRD:

- Stitching library / algorithm.
- Exact angular spacing threshold for frame capture.
- Exact blur detection method and threshold.
- Exact sphere coverage percentage that triggers the export warning.
- UI design system and component library.

---

## 9. Open Questions / Action Items

| # | Item | Owner |
|---|---|---|
| 1 | Verify Google Street View maximum file size and resolution for photosphere uploads | TBD |
| 2 | Evaluate available on-device stitching libraries for Android (e.g., OpenCV, custom pipeline) and select based on quality and performance benchmarks | TBD |
| 3 | Define exact angular spacing threshold and blur detection threshold through UX testing | TBD |
| 4 | Define the minimum sphere coverage percentage that triggers the export warning | TBD |
| 5 | Confirm ARCore device compatibility requirements align with the N-1 Android version support policy | TBD |

---

## 10. Future Considerations (Post-v1)

- iOS version (Swift + ARKit).
- Google Play Store distribution.
- Direct upload to Google Street View API via OAuth.
- In-app photosphere gallery.
- First-launch onboarding / tutorial.
- Multiple export format support (e.g., video/360° MP4).
