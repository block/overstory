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

/**
 * Calculates an overlay's top-left position from its anchor, measured size, and available area.
 *
 * Coordinates and sizes are in pixels and must not be NaN or infinite. Both bounds use the same
 * coordinate space, with left <= right and top <= bottom; sizes must be non-negative. The caller
 * excludes insets and margins from `availableBounds`. The returned position may lie outside those
 * bounds.
 */
@ExperimentalComposeOverlaysApi
public fun interface OverlayPlacementStrategy {
  /**
   * @param anchorBounds Anchor bounds in the caller's coordinate space, possibly outside the
   *   available bounds.
   * @param availableBounds Area available for placement, in the same coordinate space.
   * @param overlaySize Measured overlay size in pixels.
   * @param layoutDirection Resolves logical start/end and alignment ties.
   * @return The physical top-left position in the same coordinate space as [availableBounds].
   */
  public fun calculatePosition(
    anchorBounds: Rect,
    availableBounds: Rect,
    overlaySize: Size,
    layoutDirection: LayoutDirection,
  ): Offset
}

/**
 * Aligns an overlay with its anchor so it grows toward the center of the available bounds. An
 * anchor exactly at the center grows down and toward logical end.
 *
 * Horizontal placement is adjusted only at the chosen outer edge. Vertical placement is adjusted
 * into the available bounds before applying [gapPx]. An overlay at least as tall as the available
 * area starts at its top before that gap is applied. Oversized overlays and gaps can overflow.
 *
 * @param gapPx Signed vertical distance from the anchor, in pixels. Positive values move away from
 *   the anchor; negative values allow overlap. This is applied after the vertical edge adjustment.
 */
@ExperimentalComposeOverlaysApi
public class CenterSeekingOverlayPlacement(private val gapPx: Float = 0f) :
  OverlayPlacementStrategy {
  override fun calculatePosition(
    anchorBounds: Rect,
    availableBounds: Rect,
    overlaySize: Size,
    layoutDirection: LayoutDirection,
  ): Offset =
    inStartCoordinates(anchorBounds, availableBounds, overlaySize, layoutDirection) { anchor, area
      ->
      val growsToEnd = anchor.center.x <= area.center.x
      val growsDown = anchor.center.y <= area.center.y
      val x =
        if (growsToEnd) anchor.left.coerceAtLeast(0f)
        else (anchor.right - overlaySize.width).coerceAtMost(area.right - overlaySize.width)
      val desiredY = if (growsDown) anchor.bottom else anchor.top - overlaySize.height
      val y =
        when {
          overlaySize.height >= area.height -> 0f
          desiredY < 0f -> 0f
          desiredY + overlaySize.height > area.bottom -> area.bottom - overlaySize.height
          else -> desiredY
        }
      Offset(x, y + if (growsDown) gapPx else -gapPx)
    }
}

/** The first side to try, with the opposite side as fallback on the same axis. */
@ExperimentalComposeOverlaysApi
public enum class OverlayPlacementPreference {
  /** Try above, then below. */
  Above,
  /** Try below, then above. */
  Below,
  /** Try logical start, then end. */
  Start,
  /** Try logical end, then start. */
  End,
  /** Try above, below, logical start, then end. */
  Any,
}

/**
 * Places adjacent to the anchor using [preference]. A side fits when there is enough space between
 * that anchor edge and the corresponding available edge along the placement axis. Offscreen anchors
 * are used as supplied; a fit on one axis does not imply full containment on both axes.
 *
 * Vertical placements align to the anchor's logical start, or its logical end when space is tight.
 * Horizontal placements align to the anchor's top, shifting upward if space below is insufficient.
 * If neither side on the requested axis fits, placement falls back to the available top or logical
 * start edge. [OverlayPlacementPreference.Any] falls back to the top-start corner if no side fits.
 * Oversized overlays can extend beyond the available area.
 *
 * @param preference Side order, resolved using the supplied layout direction.
 */
@ExperimentalComposeOverlaysApi
public class DirectionalOverlayPlacement(
  private val preference: OverlayPlacementPreference = OverlayPlacementPreference.Any
) : OverlayPlacementStrategy {
  override fun calculatePosition(
    anchorBounds: Rect,
    availableBounds: Rect,
    overlaySize: Size,
    layoutDirection: LayoutDirection,
  ): Offset =
    inStartCoordinates(anchorBounds, availableBounds, overlaySize, layoutDirection) { anchor, area
      ->
      val canPlaceAbove = anchor.top - overlaySize.height >= 0f
      val canPlaceBelow = anchor.bottom + overlaySize.height <= area.bottom
      val canPlaceStart = anchor.left - overlaySize.width >= 0f
      val canPlaceEnd = anchor.right + overlaySize.width <= area.right
      val alignedY =
        if (anchor.top + overlaySize.height > area.bottom) area.bottom - overlaySize.height
        else anchor.top
      val alignedX =
        when {
          anchor.left < 0f -> 0f
          anchor.left + overlaySize.width > area.right -> anchor.right - overlaySize.width
          else -> anchor.left
        }
      when (preference) {
        OverlayPlacementPreference.Above,
        OverlayPlacementPreference.Below -> {
          val y =
            positionAlongAxis(
              anchorStart = anchor.top,
              anchorEnd = anchor.bottom,
              availableEnd = area.bottom,
              overlayExtent = overlaySize.height,
              preferStart = preference == OverlayPlacementPreference.Above,
            )
          Offset(alignedX, y)
        }
        OverlayPlacementPreference.Start,
        OverlayPlacementPreference.End -> {
          val x =
            positionAlongAxis(
              anchorStart = anchor.left,
              anchorEnd = anchor.right,
              availableEnd = area.right,
              overlayExtent = overlaySize.width,
              preferStart = preference == OverlayPlacementPreference.Start,
            )
          Offset(x, alignedY)
        }
        OverlayPlacementPreference.Any ->
          when {
            canPlaceAbove -> Offset(alignedX, anchor.top - overlaySize.height)
            canPlaceBelow -> Offset(alignedX, anchor.bottom)
            canPlaceStart -> Offset(anchor.left - overlaySize.width, alignedY)
            canPlaceEnd -> Offset(anchor.right, alignedY)
            else -> Offset.Zero
          }
      }
    }
}

/** Try the preferred side, then its opposite, in an axis whose available start is zero. */
private fun positionAlongAxis(
  anchorStart: Float,
  anchorEnd: Float,
  availableEnd: Float,
  overlayExtent: Float,
  preferStart: Boolean,
): Float {
  val beforeAnchor = anchorStart - overlayExtent
  val fitsBefore = beforeAnchor >= 0f
  val fitsAfter = anchorEnd + overlayExtent <= availableEnd
  return when {
    preferStart && fitsBefore -> beforeAnchor
    fitsAfter -> anchorEnd
    fitsBefore -> beforeAnchor
    else -> 0f
  }
}

/** Translate to the available area's origin and mirror logical start to the left for the math. */
private inline fun inStartCoordinates(
  anchor: Rect,
  available: Rect,
  size: Size,
  direction: LayoutDirection,
  calculate: (Rect, Rect) -> Offset,
): Offset {
  val localAnchor =
    Rect(
      left =
        if (direction == LayoutDirection.Ltr) anchor.left - available.left
        else available.right - anchor.right,
      top = anchor.top - available.top,
      right =
        if (direction == LayoutDirection.Ltr) anchor.right - available.left
        else available.right - anchor.left,
      bottom = anchor.bottom - available.top,
    )
  val localPosition = calculate(localAnchor, Rect(Offset.Zero, available.size))
  return Offset(
    x =
      if (direction == LayoutDirection.Ltr) available.left + localPosition.x
      else available.right - localPosition.x - size.width,
    y = available.top + localPosition.y,
  )
}
