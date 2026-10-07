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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Renders overlay content above a scrim that can block or pass through pointer input.
 *
 * The caller supplies the color, visibility, pointer, and focus policies. A scrim with
 * [blockPointerInput] consumes taps even when [onTapDetected] is null or [renderScrimBackground] is
 * false.
 *
 * @param renderScrimBackground Whether to draw the scrim background.
 * @param background The color of the scrim background.
 * @param blockPointerInput Whether this scrim blocks taps on covered content.
 * @param focusable Whether this scrim participates in focus.
 * @param requestInitialFocus Whether this scrim should claim initial focus.
 * @param modifier The modifier applied to the scrim container.
 * @param onTapDetected Optional callback for taps on a blocking scrim, with the local tap position.
 * @param scrimAlpha Opacity multiplier for the scrim background only, from 0 to 1. Does not affect
 *   [overlay] content.
 * @param overlay The content rendered above the scrim.
 */
@ExperimentalComposeOverlaysApi
@Composable
public fun OverlayScrim(
  renderScrimBackground: Boolean,
  background: Color,
  blockPointerInput: Boolean,
  focusable: Boolean,
  requestInitialFocus: Boolean,
  modifier: Modifier = Modifier,
  onTapDetected: ((Offset) -> Unit)? = null,
  scrimAlpha: Float = 1f,
  overlay: @Composable () -> Unit,
) {
  val scrimModifier =
    if (renderScrimBackground) {
      Modifier.background(
        brush =
          Brush.verticalGradient(colors = listOf(background, background), startY = 0f, endY = 1f),
        alpha = scrimAlpha,
      )
    } else {
      Modifier
    }

  Box(
    modifier
      .fillMaxSize()
      .overlayFocusTarget(enabled = focusable, requestInitialFocus = requestInitialFocus)
      .then(
        if (blockPointerInput) {
          // Always detect and consume taps to protect covered content, even when the caller has
          // no callback (for example, an overlay that cannot be dismissed by tapping outside).
          Modifier.pointerInput(onTapDetected) {
            detectTapGestures(onTap = { offset -> onTapDetected?.invoke(offset) })
          }
        } else {
          Modifier
        }
      )
      .then(scrimModifier)
  ) {
    overlay()
  }
}
