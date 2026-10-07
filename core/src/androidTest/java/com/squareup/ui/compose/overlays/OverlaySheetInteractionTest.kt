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

import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test

/** A standalone renderer with caller-selected release destinations. */
@OptIn(ExperimentalComposeOverlaysApi::class)
class OverlaySheetInteractionTest {
  @get:Rule val rule = createComposeRule()
  private lateinit var state: OverlaySheetState
  private val destination = mutableStateOf(OverlaySheetValue.Shown)

  @Test
  fun callerCanKeepSheetShownThenChangeReleasePolicy() {
    rule.setContent { Sheet() }
    dragDown()
    rule.runOnIdle {
      assertThat(state.settledValue).isEqualTo(OverlaySheetValue.Shown)
      destination.value = OverlaySheetValue.Hidden
    }
    dragDown()
    rule.runOnIdle {
      assertThat(state.settledValue).isEqualTo(OverlaySheetValue.Hidden)
      assertThat(state.offset).isEqualTo(0f)
    }
  }

  @Test
  fun restorationKeepsSettledHiddenDestination() {
    destination.value = OverlaySheetValue.Hidden
    val restoration = StateRestorationTester(rule)
    restoration.setContent { Sheet() }
    dragDown()
    val original = rule.runOnIdle { state }
    restoration.emulateSavedInstanceStateRestore()
    rule.runOnIdle {
      assertThat(state).isNotSameInstanceAs(original)
      assertThat(state.settledValue).isEqualTo(OverlaySheetValue.Hidden)
      assertThat(state.offset).isEqualTo(0f)
    }
  }

  @Composable
  private fun Sheet() {
    val height = with(LocalDensity.current) { 160.dp.toPx() }
    val sheet =
      rememberSaveable(saver = OverlaySheetState.saver(height)) {
        OverlaySheetState(initialHeight = height)
      }
    state = sheet
    val target by destination
    val policy = remember(target) { OverlaySheetSettlingPolicy { _, _, _, _ -> target } }
    val connection = remember(sheet, policy) { sheet.nestedScrollConnection(policy, snap()) }
    Box(
      Modifier.fillMaxSize()
        .nestedScroll(connection)
        .draggable(
          state = sheet.touchDragState,
          orientation = Orientation.Vertical,
          startDragImmediately = sheet.isAnimationRunning,
          onDragStopped = { velocity -> launch { sheet.settle(velocity, policy, snap()) } },
        )
    ) {
      Box(
        Modifier.align(Alignment.BottomCenter)
          .offset { IntOffset(0, (sheet.offset + height).roundToInt()) }
          .size(160.dp)
          .testTag("sheet")
      )
    }
  }

  private fun dragDown() {
    val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
    rule.onRoot().performTouchInput {
      down(Offset(bounds.center.x, bounds.top + 1f))
      moveBy(Offset(0f, bounds.height * .8f), delayMillis = 500)
      up()
    }
    rule.waitForIdle()
  }
}
