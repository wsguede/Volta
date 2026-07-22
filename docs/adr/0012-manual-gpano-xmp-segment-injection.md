# 0012 — Embed GPano XMP via manual JPEG segment injection

**Date:** 2026-07-21
**Status:** Accepted

## Context

Exported photospheres must carry the Google `GPano` XMP namespace (`UsePanoramaViewer`,
`ProjectionType`, `FullPanoWidthPixels`/`HeightPixels`, `CroppedArea*`) so Google Maps and
Street View recognise the file as an equirectangular panorama. Two approaches were available:

1. `androidx.exifinterface.media.ExifInterface.setAttribute(ExifInterface.TAG_XMP, ...)` — the
   library exposes a `TAG_XMP` constant, but its write behaviour is not documented as producing
   a standalone, spec-compliant XMP packet (the `<?xpacket ...?>` wrapper and the
   `http://ns.adobe.com/xap/1.0/` APP1 identifier expected by photosphere-aware viewers). Relying
   on it risks metadata that `ExifInterface` itself can round-trip but that Google's photosphere
   parsers reject.
2. Construct the XMP APP1 segment by hand — marker `0xFFE1`, big-endian length, the
   `http://ns.adobe.com/xap/1.0/\0` identifier, then the UTF-8 XMP packet — and splice it into
   the JPEG byte stream immediately after the SOI marker.

## Decision

Embed the `GPano` XMP packet by manually constructing and inserting the APP1 segment
(`GPanoXmpInjector`, pure Kotlin, no Android dependency) before the file is handed to
`ExifInterface` for standard EXIF GPS tag writing (`PanoramaMetadataWriter`). `ExifInterface` is
used only for the well-documented, testable parts of the format: `setLatLong` / `setAltitude`.

## Consequences

- The XMP packet structure is fully under our control and deterministically testable — unit
  tests assert on the exact byte layout and packet content without needing a device or
  Robolectric.
- We own the small risk of JPEG segment-ordering edge cases (e.g. `ExifInterface.saveAttributes()`
  rewriting the file could reorder segments relative to our injected APP1); this is mitigated by
  writing GPano first, then letting `ExifInterface` write EXIF afterward, and covered by
  `PanoramaMetadataWriterTest` asserting the packet survives the EXIF write.
- If `androidx.exifinterface` XMP write support is later confirmed spec-compliant, this can be
  revisited, but the manual approach has no external dependency risk and matches what several
  other panorama tools do.
