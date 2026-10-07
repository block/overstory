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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.fastRoundToInt

/**
 * Applies IME insets by adjusting the modified content to keep the focused child inside the insets
 * by translating the modified node. When the given insets change (and _only_ when the insets
 * change), it looks to see if the modified node has any descendants that are focused and currently
 * at least partially visible. If there are focused bounds, then it translates the entire modified
 * node to keep the focused bounds in view. After the insets have changed, it does not adjust the
 * translation until the insets change again. This simulates the Android platform behavior of
 * translating a window's contents when `softInputMode` is `ADJUST_PAN`.
 *
 * Currently only considers the bottom inset, but if we need to support more we can add the logic
 * for them.
 *
 * This modifier is unfortunately `@Composable` because it needs to read the [WindowInsets.ime]
 * composable property and share state across multiple modifier factories that aren't supported via
 * [ModifierNodeElement].
 */
@OptIn(ExperimentalFoundationApi::class)
@ExperimentalComposeOverlaysApi
@Suppress("ModifierComposable")
@Composable
public fun Modifier.imeTranslation(): Modifier {
  return insetsTranslation(WindowInsets.ime)
}

/** Separates the translation mechanics from the Android window-insets source. */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("ModifierComposable")
@Composable
internal fun Modifier.insetsTranslation(insets: WindowInsets): Modifier {
  val state = remember { InsetsTranslationState() }
  return this.then(InsetsTranslationModifier(insets, state))
    .bringIntoViewResponder(state)
    // Descendants should not apply these insets again since we're already handling them.
    .consumeWindowInsets(insets)
}

@OptIn(ExperimentalFoundationApi::class)
private class InsetsTranslationState : BringIntoViewResponder {
  var bringIntoViewResponderDelegate: BringIntoViewResponder? = null

  override suspend fun bringChildIntoView(localRect: () -> Rect?) {
    bringIntoViewResponderDelegate?.bringChildIntoView(localRect)
  }

  override fun calculateRectForParent(localRect: Rect): Rect {
    return bringIntoViewResponderDelegate?.calculateRectForParent(localRect) ?: localRect
  }
}

private data class InsetsTranslationModifier(
  val insets: WindowInsets,
  val state: InsetsTranslationState,
) : ModifierNodeElement<InsetsTranslationModifierNode>() {
  override fun create(): InsetsTranslationModifierNode =
    InsetsTranslationModifierNode(insets, state)

  override fun update(node: InsetsTranslationModifierNode) {
    node.insets = insets
  }
}

@OptIn(ExperimentalFoundationApi::class)
private class InsetsTranslationModifierNode(
  var insets: WindowInsets,
  val state: InsetsTranslationState,
) : Modifier.Node(), LayoutModifierNode, BringIntoViewResponder {

  private var translationY by mutableIntStateOf(0)
  private var lastBottomInset = 0
  private var focusedBounds: Rect? = null

  override fun onAttach() {
    state.bringIntoViewResponderDelegate = this
  }

  override fun onDetach() {
    state.bringIntoViewResponderDelegate = null
  }

  override fun MeasureScope.measure(
    measurable: Measurable,
    constraints: Constraints,
  ): MeasureResult {
    val placeable = measurable.measure(constraints)
    return layout(placeable.width, placeable.height) {
      val bottomInset = insets.getBottom(density = this@measure)
      if (bottomInset != lastBottomInset) {
        lastBottomInset = bottomInset
        updateTranslation()
      }
      placeable.place(0, translationY)
    }
  }

  override suspend fun bringChildIntoView(localRect: () -> Rect?) {
    // We don't need to read this lazily since we're not doing any animations.
    focusedBounds = localRect()
    updateTranslation()
  }

  override fun calculateRectForParent(localRect: Rect): Rect {
    // This method is only needed in case there's a responder above this one, but since we're only
    // using this modifier on top-level overlay containers we needn't bother doing the math.
    return localRect
  }

  private fun updateTranslation() {
    val myCoordinates = requireLayoutCoordinates().takeIf { it.isAttached }
    // The focused bounds will be inside the graphics layer that we're transforming, so we need to
    // untransform it.
    val localFocusedBounds = focusedBounds
    if (myCoordinates == null || localFocusedBounds == null) {
      translationY = 0
      return
    }

    // Assume the inset is reported relative to root, which is the view hosting this composable. In
    // most uses of Compose Overlays, this should be the whole window, otherwise assume that the
    // insets
    // are already adjusted to the host view. The modified node may only be much smaller inside the
    // root though, so we need to adjust the inset value to account for our bounds.
    val myBoundsInRoot = myCoordinates.boundsInRoot()
    val focusedBoundsInRoot = myCoordinates.localToRoot(localFocusedBounds)
    val root = myCoordinates.findRootCoordinates()
    val localBottomInset =
      (lastBottomInset - (root.size.height - myBoundsInRoot.bottom)).coerceAtLeast(0f)
    val insetBoundsInRoot = myBoundsInRoot.copy(bottom = myBoundsInRoot.bottom - localBottomInset)
    val focusedTopAboveViewport = focusedBoundsInRoot.top < insetBoundsInRoot.top
    val focusedBottomBelowViewport = focusedBoundsInRoot.bottom > insetBoundsInRoot.bottom

    // Offsetting something without animation is jarring, so try to move it around as little as
    // possible by only doing so when the focused bounds are not actually in view.
    val newTranslationY =
      if (focusedTopAboveViewport && !focusedBottomBelowViewport) {
        // The focused bounds are above the viewport so we need to shift it down.
        (insetBoundsInRoot.top - focusedBoundsInRoot.top).fastRoundToInt()
      } else if (focusedBottomBelowViewport && !focusedTopAboveViewport) {
        // The focused bounds are below the viewport so we need to shift it up.
        (insetBoundsInRoot.bottom - focusedBoundsInRoot.bottom).fastRoundToInt()
      } else {
        // Either:
        //  - The focused bounds intersect fully with the viewport and it's bigger than it, so we
        // can't
        //    make it any more visible, or
        //  - The focused bounds is fully contained in the viewport.
        // In either case we can't improve on the situation by changing the translation.
        translationY
      }

    // Constrain the translation so it's never translated higher than the bottom inset or below its
    // natural position.
    translationY = newTranslationY.coerceIn(-localBottomInset.toInt(), 0)
  }
}

private fun LayoutCoordinates.localToRoot(bounds: Rect): Rect =
  Rect(topLeft = localToRoot(bounds.topLeft), bottomRight = localToRoot(bounds.bottomRight))
