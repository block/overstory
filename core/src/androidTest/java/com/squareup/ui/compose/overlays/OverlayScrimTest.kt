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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalComposeOverlaysApi::class)
class OverlayScrimTest {

  @get:Rule val rule = createComposeRule()

  @Test
  fun scrimAlphaDoesNotFadeOverlayContent() {
    val scrimAlpha = mutableFloatStateOf(1f)
    rule.setContent {
      Box(Modifier.size(80.dp).background(Color.White).testTag(scrimTag)) {
        OverlayScrim(
          renderScrimBackground = true,
          background = Color.Black,
          blockPointerInput = false,
          focusable = false,
          requestInitialFocus = false,
          scrimAlpha = scrimAlpha.floatValue,
        ) {
          Box(Modifier.size(20.dp).background(Color.Red))
        }
      }
    }

    val opaque = rule.onNodeWithTag(scrimTag).captureToImage().toPixelMap()
    assertThat(opaque[opaque.width - 1, opaque.height - 1]).isEqualTo(Color.Black)
    assertThat(opaque[1, 1]).isEqualTo(Color.Red)

    rule.runOnIdle { scrimAlpha.floatValue = 0f }

    val transparent = rule.onNodeWithTag(scrimTag).captureToImage().toPixelMap()
    assertThat(transparent[transparent.width - 1, transparent.height - 1]).isEqualTo(Color.White)
    assertThat(transparent[1, 1]).isEqualTo(Color.Red)
  }

  @Test
  fun blockingScrimConsumesTapWithoutBackgroundOrCallback() {
    var coveredTaps = 0
    rule.setContent {
      Box {
        Box(Modifier.fillMaxSize().clickable { coveredTaps++ })
        OverlayScrim(
          renderScrimBackground = false,
          background = Color.Transparent,
          blockPointerInput = true,
          focusable = true,
          requestInitialFocus = true,
          modifier = Modifier.testTag(scrimTag),
          overlay = {},
        )
      }
    }

    rule.onNodeWithTag(scrimTag).performTouchInput { click() }

    rule.runOnIdle { assertThat(coveredTaps).isEqualTo(0) }
  }

  @Test
  fun pointerPassThroughCanStillClaimFocus() {
    var coveredTaps = 0
    rule.setContent {
      Box {
        Box(Modifier.fillMaxSize().clickable { coveredTaps++ })
        OverlayScrim(
          renderScrimBackground = false,
          background = Color.Transparent,
          blockPointerInput = false,
          focusable = true,
          requestInitialFocus = true,
          modifier = Modifier.testTag(scrimTag),
          overlay = {},
        )
      }
    }

    rule.onNodeWithTag(scrimTag).assertIsFocused().performTouchInput { click() }

    rule.runOnIdle { assertThat(coveredTaps).isEqualTo(1) }
  }

  @Test
  fun blockingScrimReportsLocalTapPosition() {
    var tappedAt: Offset? = null
    rule.setContent {
      OverlayScrim(
        renderScrimBackground = true,
        background = Color.Black,
        blockPointerInput = true,
        focusable = true,
        requestInitialFocus = true,
        modifier = Modifier.testTag(scrimTag),
        onTapDetected = { tappedAt = it },
        overlay = {},
      )
    }

    rule.onNodeWithTag(scrimTag).performTouchInput { click(Offset(20f, 30f)) }

    rule.runOnIdle { assertThat(tappedAt).isEqualTo(Offset(20f, 30f)) }
  }

  @Test
  fun nonFocusableBlockingScrimPreservesCoveredFocus() {
    assertNonFocusableScrimPreservesCoveredFocus(blockPointerInput = true)
  }

  @Test
  fun nonFocusablePassThroughScrimPreservesCoveredFocus() {
    assertNonFocusableScrimPreservesCoveredFocus(blockPointerInput = false)
  }

  private fun assertNonFocusableScrimPreservesCoveredFocus(blockPointerInput: Boolean) {
    val showScrim = mutableStateOf(false)
    var coveredTaps = 0
    rule.setContent {
      Box {
        Box(Modifier.fillMaxSize().clickable { coveredTaps++ }) {
          Box(Modifier.size(48.dp).testTag("covered focus").focusable())
        }
        if (showScrim.value) {
          OverlayScrim(
            renderScrimBackground = false,
            background = Color.Transparent,
            blockPointerInput = blockPointerInput,
            focusable = false,
            requestInitialFocus = true,
            modifier = Modifier.testTag(scrimTag),
            overlay = {},
          )
        }
      }
    }

    rule.onNodeWithTag("covered focus", useUnmergedTree = true).requestFocus().assertIsFocused()
    rule.runOnIdle { showScrim.value = true }

    rule.onNodeWithTag("covered focus", useUnmergedTree = true).assertIsFocused()
    rule.onNodeWithTag(scrimTag).performTouchInput { click() }
    rule.onNodeWithTag("covered focus", useUnmergedTree = true).assertIsFocused()
    rule.runOnIdle { assertThat(coveredTaps).isEqualTo(if (blockPointerInput) 0 else 1) }
  }

  private companion object {
    const val scrimTag = "overlay scrim"
  }
}
