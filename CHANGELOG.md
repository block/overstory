# Changelog

## Unreleased

### Behavior changes

- `OverlayHost` and `ViewCompatibleOverlayHost` now cap covered entries at `STARTED` by default,
  instead of `CREATED`. The topmost entry is still the only one that can resume. A covered entry is
  still visible, so it now pauses when another entry covers it, and no longer stops. Content that
  treats `ON_STOP` as no longer being shown, such as embedded Views that release their state, keeps
  working while it is covered. Work gated on `STARTED`, such as `collectAsStateWithLifecycle`,
  keeps running in covered entries. To keep the previous behavior, pass
  `maxLifecycleState = { _, isTopmost -> if (isTopmost) State.RESUMED else State.CREATED }`.

### Compatibility

- The supported Kotlin baseline is now 2.4.10, matching the Kotlin Gradle plugin and Compose compiler
  plugin used to build the artifacts. Compose, lifecycle, Android runtime, bytecode, and API
  requirements are unchanged from 0.3.1.

## 0.3.1 — 2026-10-19

### Compatibility

- Dependency, build, runtime, and API requirements are unchanged from 0.3.0.

### Fixes

- A covered `Modifier.overlayFocusLayer` no longer restores its saved focus over a view outside its
  composition that has focus once the covering overlay leaves, such as an `EditText` beside the
  `ComposeView` that a dismiss callback focuses.
- `Modifier.overlayFocusLayer` restores focus when the covering overlay is in a separate
  composition, such as a sibling `ComposeView`. Removing that overlay could make Android move
  focus into the layer and then clear it during the next layout pass, so the layer neither restored
  its saved focus nor kept the focus Android gave it. The layer now waits for pending layout, for up
  to half a second, before it restores.

## 0.3.0 — 2026-10-14

### Compatibility

- Dependency, build, and runtime requirements are unchanged from 0.2.0. The new API is additive
  and requires opting in to `ExperimentalComposeOverlaysApi`.

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
