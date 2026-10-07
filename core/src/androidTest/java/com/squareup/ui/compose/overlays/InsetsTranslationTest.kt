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

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.roundToIntRect
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalComposeOverlaysApi::class)
class InsetsTranslationTest {

  @get:Rule val rule = createComposeRule()

  private val insets = mutableStateOf(WindowInsets(bottom = 0))

  @Test
  fun focusedChildCoveredByInsetsTranslatesIntoView() {
    setContent(focusAlignment = Alignment.BottomCenter)
    rule.onNodeWithTag(focusableTag).requestFocus().assertIsFocused()
    val beforeBounds = rule.onNodeWithTag(contentTag).getUnclippedBoundsPxInRoot()

    setInsets(bottom = 100)

    val afterBounds = rule.onNodeWithTag(contentTag).getUnclippedBoundsPxInRoot()
    assertThat(afterBounds.top).isEqualTo(beforeBounds.top - 100)
    assertThat(afterBounds.bottom).isEqualTo(beforeBounds.bottom - 100)
  }

  @Test
  fun focusedChildAboveInsetsDoesNotTranslate() {
    setContent(focusAlignment = Alignment.TopCenter)
    rule.onNodeWithTag(focusableTag).requestFocus().assertIsFocused()
    val beforeBounds = rule.onNodeWithTag(contentTag).getUnclippedBoundsPxInRoot()

    setInsets(bottom = 100)

    val afterBounds = rule.onNodeWithTag(contentTag).getUnclippedBoundsPxInRoot()
    assertThat(afterBounds).isEqualTo(beforeBounds)
  }

  @Test
  fun noFocusedChildDoesNotTranslate() {
    setContent(focusAlignment = Alignment.BottomCenter)
    val beforeBounds = rule.onNodeWithTag(contentTag).getUnclippedBoundsPxInRoot()

    setInsets(bottom = 100)

    val afterBounds = rule.onNodeWithTag(contentTag).getUnclippedBoundsPxInRoot()
    assertThat(afterBounds).isEqualTo(beforeBounds)
  }

  @Test
  fun clearingInsetsResetsTranslation() {
    setContent(focusAlignment = Alignment.BottomCenter)
    rule.onNodeWithTag(focusableTag).requestFocus().assertIsFocused()
    val initialBounds = rule.onNodeWithTag(contentTag).getUnclippedBoundsPxInRoot()

    setInsets(bottom = 100)
    val translatedBounds = rule.onNodeWithTag(contentTag).getUnclippedBoundsPxInRoot()
    assertThat(translatedBounds.top).isEqualTo(initialBounds.top - 100)

    setInsets(bottom = 0)

    val finalBounds = rule.onNodeWithTag(contentTag).getUnclippedBoundsPxInRoot()
    assertThat(finalBounds).isEqualTo(initialBounds)
  }

  private fun setContent(focusAlignment: Alignment) {
    rule.setContent {
      Box(Modifier.insetsTranslation(insets.value)) {
        Box(Modifier.testTag(contentTag).fillMaxSize()) {
          BasicText(
            text = "Focusable content",
            modifier = Modifier.testTag(focusableTag).align(focusAlignment).focusable(),
          )
        }
      }
    }
  }

  private fun SemanticsNodeInteraction.getUnclippedBoundsPxInRoot(): IntRect {
    val node = fetchSemanticsNode("Failed to retrieve bounds of the node.")
    return with(node.layoutInfo.density) { getUnclippedBoundsInRoot().toRect().roundToIntRect() }
  }

  private fun setInsets(bottom: Int) {
    rule.runOnIdle { insets.value = WindowInsets(bottom = bottom) }
  }

  private companion object {
    const val contentTag = "translated content"
    const val focusableTag = "focusable content"
  }
}
