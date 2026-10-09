# Changelog

## Unreleased

### Changes

- Add experimental `Modifier.overlayFocusLayer` for content that overlays can cover. A covered
  layer releases focus, including captured focus and focus that a View embedded in it takes, and
  keeps focus out of its content. When it is uncovered, it restores the descendant that had focus.
- `Modifier.overlayFocusTarget` waits to request initial focus while it is inside a covered layer.
  It requests it again when the layer is uncovered without restoring focus, and when a covered
  layer in its composition, or in a composition embedded in it, releases focus.
- Overlay focus targets and layers that are being removed keep focus away from themselves and their
  content. A focus search that runs while they detach, such as when Android hands back the focus
  of a removed view, could otherwise leave focus on a detached node.

## 0.2.0 — 2026-10-07

### Compatibility

- The Compose dependency baseline increases from 1.9.5 to **1.12.1**. Applications consuming
  Overstory must support the newer Compose dependencies; this release can upgrade Compose in
  an application's resolved dependency graph.
- Compose 1.12.1 requires consumers to use **compileSdk 37** and **Android Gradle Plugin 9.1
  or later**. Update the consuming application's build configuration before adopting 0.2.0.
- Building this repository now uses Android SDK 37, Android Gradle Plugin 9.4.1, and Gradle 9.8.0.
  Java 21 is still required to run the build. See [README.md](README.md#build-and-test) for setup.
- The minimum Android runtime remains API 24, bytecode targets Java 11, and Kotlin remains 2.3.21.
  Maven coordinates and Kotlin package names are unchanged.

### Changes

- Leave outward fling velocity available to parent scrollers and overscroll effects at sheet
  boundaries, while still completing settling when a drag reaches the opposite anchor.
- Upgrade Compose in both library modules and the independent artifact consumer.
- Adapt API documentation generation and Kotlin configuration to Android Gradle Plugin 9.

## 0.1.0 — 2026-10-07

Initial Maven Central release of `xyz.block.overstory:core` and
`xyz.block.overstory:view-compat`, with Compose 1.9.5 as the dependency baseline.
