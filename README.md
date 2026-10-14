# Overstory

Sheets, modals, and anchored overlays for Jetpack Compose, in your app's existing window.

Overstory is a toolkit for building a consistent overlay system around your Android app's design
and navigation. It renders overlays above screen content in the same Compose hierarchy, with
keyed state and lifecycle, focus and input helpers, placement, and sheet gestures.
You supply the visual design. The API is experimental.

## Why Overstory?

- **One place to coordinate overlays.** Drive a bottom-to-top stack from your own models. Stable
  keys preserve each entry's state and lifecycle when reordered; covered entries have a configurable
  lifecycle cap.
- **Your design, reusable mechanics.** Combine scrims, focus helpers, anchor tracking, placement
  strategies, and draggable sheets with your own layouts, animations, and dismissal rules.
- **No extra dialog window.** Unlike platform `Dialog` and Android's
  [Compose `Dialog`](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/window/AndroidDialog.android.kt),
  `OverlayHost` does not create a separate window or dialog composition. Overlay content participates
  in the host's layout and rendering, avoiding a separate dialog window's setup and teardown.

Choose Overstory when you need a custom overlay system with shared stacking and interaction policies.
For a standard confirmation, Material `AlertDialog` is simpler. Overstory is not a drop-in dialog:
applications wire Back handling, dismissal, focus and accessibility behavior, insets, and navigation
restoration. Overlays share the host window's bounds and window-level settings; use a separate window
when you need independent window behavior.

Keeping overlays in one window avoids additional dialog-window setup and surface management.
In [Square's reported comparisons (11–14 minutes)](https://www.youtube.com/watch?v=qZyT3j6Y2IU&t=660s),
the original Compose overlays delivered faster frames and a 14% smaller shallow heap than Square's
Android-dialog implementation. These results reflect Square's tested flows; profile your own
content and devices.

## Modules

- `xyz.block.overstory:core`: Compose host, focus, scrim, anchor registration, placement,
  and two-anchor sheet mechanics.
- `xyz.block.overstory:view-compat`: optional Android View saved-state integration, built
  on core. Compose-only applications need only core.

The Kotlin packages are `com.squareup.ui.compose.overlays` and
`com.squareup.ui.compose.overlays.viewcompat`.

```mermaid
flowchart LR
  O["Overstory core + optional view-compat"] --> A["Versioned Maven artifacts"]
  A --> R["Application renderer: visuals, settling policy, dismissal"]
  A --> C["Standalone artifact consumer test"]
  R --> N["Application navigation and overlay models"]
```

See the [engine guide](docs/engine-guide.md) and the
[saved-state compatibility decision](docs/saveable-registry.md).

## Build and test

Requires Java 21 and Android SDK 37. Set `ANDROID_HOME` to your SDK installation to configure
both Gradle builds. Alternatively, set `sdk.dir` in both `local.properties` at the repository root
and `consumer-test/local.properties`; these files are untracked. The consumer is a separate Gradle
build and does not read the root build's `local.properties`.
Install `platform-tools`, `platforms;android-37.0`, and `build-tools;37.0.0` with the Android SDK Manager.
Minimum Android version is API 24; bytecode targets Java 11.
The supported dependency baseline is Kotlin 2.3.21, Compose 1.12.1, lifecycle 2.9.4,
savedstate 1.3.3, Android Gradle Plugin 9.4.1, and Gradle 9.8.0.
Dependencies resolve from Google Maven and Maven Central; Gradle plugins also use the Gradle Plugin Portal.
No publishing credentials are needed to build or test.

```sh
./gradlew testDebugUnitTest checkKotlinAbi ktfmtCheck lintRelease assembleDebugAndroidTest
./gradlew -Pversion=0.1.0-SNAPSHOT publishAllPublicationsToStagingRepository
./gradlew -p consumer-test -PoverstoryVersion=0.1.0-SNAPSHOT assembleDebug assembleDebugAndroidTest

# With a connected device or emulator (API 24+); select it with ANDROID_SERIAL.
./gradlew :core:connectedDebugAndroidTest :view-compat:connectedDebugAndroidTest
./gradlew -p consumer-test -PoverstoryVersion=0.1.0-SNAPSHOT connectedDebugAndroidTest
```

Device tests exercise animations, so leave the device's animation scales enabled.
GitHub Actions runs these checks with an API 35 emulator.

`consumer-test` is a separate Gradle build. It resolves Maven artifacts from `build/repository`,
never project dependencies or included builds. Its smoke test opens an overlay, edits text,
recreates the Activity, verifies restored text, and dismisses the overlay. Library modules contain
portable behavior tests; applications should test their own rendering and navigation policies.

## Using a local build

See [artifact staging and releases](docs/publishing.md) for local builds and signed Maven Central
releases. Use a version that has completed publication; repository tags alone do not guarantee
that artifacts are available.

## Project resources

- Optional background: [Reimagining Android Dialogs with Jetpack Compose](https://www.youtube.com/watch?v=qZyT3j6Y2IU)
  By Keith Abdulla, droidcon NYC 2025.
- [Contributing](CONTRIBUTING.md)
- [Releasing](RELEASING.md)
- [Changelog](CHANGELOG.md)
- [Code owners](CODEOWNERS)
- [Governance](GOVERNANCE.md)
- [Code of conduct](https://github.com/block/.github/blob/main/CODE_OF_CONDUCT.md)
- [Apache 2.0 license](LICENSE)

## Acknowledgments

Overstory builds on the Compose overlay implementation originally developed by
[Keith Abdulla (@ekeitho)](https://github.com/ekeitho), beginning in 2024. Keith authored
most of that original implementation, which formed the foundation of this library.

## License

    Copyright 2024-2026 Block, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
