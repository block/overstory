/*
 * Copyright (C) 2024-2026 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.ui.compose.overlays

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

@OptIn(ExperimentalComposeOverlaysApi::class)
class OverlayPlacementStrategyTest {
  private val area = Rect(0f, 0f, 300f, 400f)
  private val size = Size(100f, 80f)
  private val centeredAnchor = Rect(130f, 180f, 170f, 220f)

  @Test
  fun centerSeekingPlacementGrowsTowardCenterAndResolvesTiesTowardEnd() {
    val strategy = CenterSeekingOverlayPlacement()
    assertPosition(strategy, Rect(20f, 30f, 60f, 70f), Offset(20f, 70f))
    assertPosition(strategy, Rect(240f, 30f, 280f, 70f), Offset(180f, 70f))
    assertPosition(strategy, Rect(20f, 300f, 60f, 340f), Offset(20f, 220f))
    assertPosition(strategy, Rect(240f, 300f, 280f, 340f), Offset(180f, 220f))
    assertPosition(strategy, centeredAnchor, Offset(130f, 220f))
    assertPosition(strategy, centeredAnchor, Offset(70f, 220f), direction = LayoutDirection.Rtl)
  }

  @Test
  fun gapsAreSignedPixelsAppliedAfterVerticalEdgeAdjustment() {
    val strategy = CenterSeekingOverlayPlacement(gapPx = 12f)
    assertPosition(strategy, Rect(20f, 30f, 60f, 70f), Offset(20f, 82f))
    assertPosition(strategy, Rect(240f, 300f, 280f, 340f), Offset(180f, 208f))
    assertPosition(strategy, Rect(-40f, -50f, -10f, -20f), Offset(0f, 12f))
    assertPosition(strategy, Rect(310f, 420f, 350f, 460f), Offset(200f, 308f))
    assertPosition(CenterSeekingOverlayPlacement(-12f), centeredAnchor, Offset(130f, 208f))
  }

  @Test
  fun centerSeekingPlacementHandlesOversizedAndZeroSizedOverlays() {
    val strategy = CenterSeekingOverlayPlacement(gapPx = 12f)
    assertPosition(strategy, Rect(20f, 30f, 60f, 70f), Offset(20f, 12f), Size(500f, 600f))
    assertPosition(strategy, Rect(240f, 300f, 280f, 340f), Offset(-220f, -12f), Size(500f, 600f))
    assertPosition(strategy, centeredAnchor, Offset(130f, 12f), Size(100f, 400f))
    assertPosition(strategy, centeredAnchor, Offset(130f, 232f), Size.Zero)
  }

  @Test
  fun directionalPreferencesResolveLogicalStartAndEnd() {
    val positions =
      listOf(
        Triple(OverlayPlacementPreference.Above, Offset(130f, 100f), Offset(70f, 100f)),
        Triple(OverlayPlacementPreference.Below, Offset(130f, 220f), Offset(70f, 220f)),
        Triple(OverlayPlacementPreference.Start, Offset(30f, 180f), Offset(170f, 180f)),
        Triple(OverlayPlacementPreference.End, Offset(170f, 180f), Offset(30f, 180f)),
        Triple(OverlayPlacementPreference.Any, Offset(130f, 100f), Offset(70f, 100f)),
      )
    for ((preference, ltr, rtl) in positions) {
      val strategy = DirectionalOverlayPlacement(preference)
      assertPosition(strategy, centeredAnchor, ltr)
      assertPosition(strategy, centeredAnchor, rtl, direction = LayoutDirection.Rtl)
    }
  }

  @Test
  fun directionalPlacementFallsBackToTheOppositeSide() {
    assertPosition(
      DirectionalOverlayPlacement(OverlayPlacementPreference.Above),
      Rect(20f, 10f, 60f, 50f),
      Offset(20f, 50f),
    )
    assertPosition(
      DirectionalOverlayPlacement(OverlayPlacementPreference.Below),
      Rect(20f, 330f, 60f, 370f),
      Offset(20f, 250f),
    )
    assertPosition(
      DirectionalOverlayPlacement(OverlayPlacementPreference.Start),
      Rect(20f, 30f, 60f, 70f),
      Offset(60f, 30f),
    )
    assertPosition(
      DirectionalOverlayPlacement(OverlayPlacementPreference.End),
      Rect(240f, 30f, 280f, 70f),
      Offset(140f, 30f),
    )
    assertPosition(
      DirectionalOverlayPlacement(OverlayPlacementPreference.Start),
      Rect(240f, 30f, 280f, 70f),
      Offset(140f, 30f),
      direction = LayoutDirection.Rtl,
    )
    assertPosition(DirectionalOverlayPlacement(), Rect(130f, 30f, 170f, 370f), Offset(30f, 30f))
  }

  @Test
  fun verticalPlacementsKeepStartAlignmentWhenTheOverlayFits() {
    for (preference in
      listOf(
        OverlayPlacementPreference.Above,
        OverlayPlacementPreference.Below,
        OverlayPlacementPreference.Any,
      )) {
      for (width in listOf(270f, 280f)) {
        val expectedY = if (preference == OverlayPlacementPreference.Below) 220f else 100f
        assertPositionAcrossCoordinateSpaces(
          DirectionalOverlayPlacement(preference),
          Rect(20f, 180f, 40f, 220f),
          Offset(20f, expectedY),
          Size(width, 80f),
        )
      }
    }
  }

  @Test
  fun horizontalPlacementsKeepTopAlignmentWhenTheOverlayFits() {
    for (preference in
      listOf(
        OverlayPlacementPreference.Start,
        OverlayPlacementPreference.End,
        OverlayPlacementPreference.Any,
      )) {
      for (height in listOf(370f, 380f)) {
        val expectedX = if (preference == OverlayPlacementPreference.End) 170f else 30f
        assertPositionAcrossCoordinateSpaces(
          DirectionalOverlayPlacement(preference),
          Rect(130f, 20f, 170f, 40f),
          Offset(expectedX, 20f),
          Size(100f, height),
        )
      }
    }
  }

  @Test
  fun noSideFitsFallsBackToTheAvailableEdgeWithoutResizing() {
    val large = Size(500f, 600f)
    assertPosition(DirectionalOverlayPlacement(), centeredAnchor, Offset.Zero, large)
    assertPosition(
      DirectionalOverlayPlacement(OverlayPlacementPreference.Above),
      centeredAnchor,
      Offset(-330f, 0f),
      large,
    )
    assertPosition(
      DirectionalOverlayPlacement(OverlayPlacementPreference.Start),
      centeredAnchor,
      Offset(0f, -200f),
      large,
    )
    assertPosition(
      DirectionalOverlayPlacement(),
      centeredAnchor,
      Offset(-200f, 0f),
      large,
      LayoutDirection.Rtl,
    )
  }

  @Test
  fun offscreenAnchorIsUsedAsSupplied() {
    assertPosition(DirectionalOverlayPlacement(), Rect(-40f, -50f, -10f, -20f), Offset(0f, -20f))
    assertPosition(
      DirectionalOverlayPlacement(OverlayPlacementPreference.Start),
      Rect(240f, 330f, 280f, 370f),
      Offset(140f, 320f),
    )
  }

  @Test
  fun fractionalPixelCoordinatesAreNotRounded() {
    assertPosition(
      CenterSeekingOverlayPlacement(2.75f),
      Rect(20.25f, 30.5f, 60.25f, 70.5f),
      Offset(20.25f, 73.25f),
      Size(100.5f, 80.25f),
    )
    assertPosition(
      DirectionalOverlayPlacement(OverlayPlacementPreference.Above),
      Rect(130.25f, 180.5f, 170.25f, 220.5f),
      Offset(130.25f, 100.25f),
      Size(100.5f, 80.25f),
    )
  }

  @Test
  fun translatingTheCoordinateSpaceTranslatesEveryResult() {
    val translation = Offset(45f, -70f)
    for (strategy in strategies()) {
      for (direction in LayoutDirection.entries) {
        for (anchor in anchors()) {
          val original = strategy.calculatePosition(anchor, area, size, direction)
          val translated =
            strategy.calculatePosition(
              anchor.translate(translation),
              area.translate(translation),
              size,
              direction,
            )
          assertWithMessage("%s %s %s", strategy, direction, anchor)
            .that(translated)
            .isEqualTo(original + translation)
        }
      }
    }
  }

  @Test
  fun rtlMirrorsLtrGeometryIncludingOversizedFallbacks() {
    for (strategy in strategies()) {
      for (overlaySize in listOf(size, Size(500f, 600f))) {
        for (anchor in anchors()) {
          val ltr = strategy.calculatePosition(anchor, area, overlaySize, LayoutDirection.Ltr)
          val mirroredAnchor =
            Rect(area.right - anchor.right, anchor.top, area.right - anchor.left, anchor.bottom)
          val rtl =
            strategy.calculatePosition(mirroredAnchor, area, overlaySize, LayoutDirection.Rtl)
          assertThat(rtl).isEqualTo(Offset(area.right - ltr.x - overlaySize.width, ltr.y))
        }
      }
    }
  }

  private fun strategies(): List<OverlayPlacementStrategy> =
    listOf(CenterSeekingOverlayPlacement(12f)) +
      OverlayPlacementPreference.entries.map(::DirectionalOverlayPlacement)

  private fun anchors(): List<Rect> =
    listOf(
      centeredAnchor,
      Rect(20f, 30f, 60f, 70f),
      Rect(240f, 300f, 280f, 340f),
      Rect(-40f, -50f, -10f, -20f),
      Rect(310f, 420f, 350f, 460f),
    )

  private fun assertPositionAcrossCoordinateSpaces(
    strategy: OverlayPlacementStrategy,
    anchor: Rect,
    expected: Offset,
    overlaySize: Size,
  ) {
    for (direction in LayoutDirection.entries) {
      for (origin in listOf(Offset.Zero, Offset(45f, -70f))) {
        val directedAnchor =
          if (direction == LayoutDirection.Ltr) anchor
          else Rect(area.right - anchor.right, anchor.top, area.right - anchor.left, anchor.bottom)
        val directedExpected =
          if (direction == LayoutDirection.Ltr) expected
          else Offset(area.right - expected.x - overlaySize.width, expected.y)
        assertWithMessage(
            "%s anchor=%s size=%s direction=%s origin=%s",
            strategy,
            anchor,
            overlaySize,
            direction,
            origin,
          )
          .that(
            strategy.calculatePosition(
              directedAnchor.translate(origin),
              area.translate(origin),
              overlaySize,
              direction,
            )
          )
          .isEqualTo(directedExpected + origin)
      }
    }
  }

  private fun assertPosition(
    strategy: OverlayPlacementStrategy,
    anchor: Rect,
    expected: Offset,
    overlaySize: Size = size,
    direction: LayoutDirection = LayoutDirection.Ltr,
  ) {
    assertWithMessage("%s anchor=%s size=%s direction=%s", strategy, anchor, overlaySize, direction)
      .that(strategy.calculatePosition(anchor, area, overlaySize, direction))
      .isEqualTo(expected)
  }
}
