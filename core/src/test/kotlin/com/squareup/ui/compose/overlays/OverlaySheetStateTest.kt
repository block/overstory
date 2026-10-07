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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test

@OptIn(ExperimentalComposeOverlaysApi::class)
class OverlaySheetStateTest {
  private val hideOnRelease = OverlaySheetSettlingPolicy { _, _, _, _ -> OverlaySheetValue.Hidden }

  @Test
  fun releasePolicyReceivesGeometryAndVelocityAndControlsDestination() = runBlocking {
    val clock = BroadcastFrameClock()
    val state = OverlaySheetState(initialHeight = 240f)
    state.touchDragState.dispatchRawDelta(180f)
    var calls = 0
    val policy = OverlaySheetSettlingPolicy { offset, shownOffset, settledValue, velocity ->
      calls++
      assertThat(offset).isEqualTo(-60f)
      assertThat(shownOffset).isEqualTo(-240f)
      assertThat(settledValue).isEqualTo(OverlaySheetValue.Shown)
      assertThat(velocity).isEqualTo(900f)
      // A caller can keep the sheet shown even after a large, fast drag toward hidden.
      OverlaySheetValue.Shown
    }
    val settling =
      launch(clock, start = CoroutineStart.UNDISPATCHED) { state.settle(900f, policy, snap()) }
    clock.sendFrame(0L)
    settling.join()
    assertThat(calls).isEqualTo(1)
    assertThat(state.offset).isEqualTo(-240f)
    assertThat(state.settledValue).isEqualTo(OverlaySheetValue.Shown)
  }

  @Test
  fun invalidResizeDoesNotChangeExistingGeometry() = runBlocking {
    val state = OverlaySheetState(initialHeight = 100f)
    for (height in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
      val failure =
        runCatching { state.updateHeight(height, animationSpec = snap()) }.exceptionOrNull()
      assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
      assertThat(state.offset).isEqualTo(-100f)
    }
  }

  @Test
  fun nestedFlingLeavesHorizontalVelocityForOtherConsumers() = runBlocking {
    for (preFling in listOf(true, false)) {
      val clock = BroadcastFrameClock()
      val state = OverlaySheetState(initialHeight = 100f)
      state.touchDragState.dispatchRawDelta(30f)
      val connection = state.nestedScrollConnection(hideOnRelease, snap())
      var consumed = Velocity.Zero
      val fling =
        launch(clock, start = CoroutineStart.UNDISPATCHED) {
          val available = Velocity(42f, -80f)
          consumed =
            if (preFling) connection.onPreFling(available)
            else connection.onPostFling(Velocity.Zero, available)
        }
      clock.sendFrame(0L)
      fling.join()
      assertThat(consumed).isEqualTo(Velocity(0f, -80f))
    }
  }

  @Test
  fun outwardPostFlingLeavesAnchoredSheetAndVelocityAlone() = runBlocking {
    for (initialValue in OverlaySheetValue.entries) {
      val clock = BroadcastFrameClock()
      val state = OverlaySheetState(initialValue, initialHeight = 100f)
      val initialOffset = state.offset
      var calls = 0
      val policy = OverlaySheetSettlingPolicy { _, _, _, _ ->
        calls++
        if (initialValue == OverlaySheetValue.Shown) OverlaySheetValue.Hidden
        else OverlaySheetValue.Shown
      }
      val connection = state.nestedScrollConnection(policy, snap())
      val available = Velocity(42f, if (initialValue == OverlaySheetValue.Shown) -80f else 80f)
      var consumed = Velocity.Zero
      val fling =
        launch(clock, start = CoroutineStart.UNDISPATCHED) {
          consumed = connection.onPostFling(Velocity.Zero, available)
        }
      clock.sendFrame(0L)
      fling.join()
      assertThat(calls).isEqualTo(0)
      assertThat(state.offset).isEqualTo(initialOffset)
      assertThat(state.settledValue).isEqualTo(initialValue)
      assertThat(consumed).isEqualTo(Velocity.Zero)
    }
  }

  @Test
  fun outwardPostFlingCompletesDragToOppositeAnchor() = runBlocking {
    for (initialValue in OverlaySheetValue.entries) {
      val clock = BroadcastFrameClock()
      val state = OverlaySheetState(initialValue, initialHeight = 100f)
      val destination =
        if (initialValue == OverlaySheetValue.Shown) OverlaySheetValue.Hidden
        else OverlaySheetValue.Shown
      var calls = 0
      val policy = OverlaySheetSettlingPolicy { _, _, _, _ ->
        calls++
        destination
      }
      val connection = state.nestedScrollConnection(policy, snap())
      val direction = if (destination == OverlaySheetValue.Hidden) 1f else -1f
      connection.onPostScroll(
        Offset.Zero,
        Offset(0f, direction * 100f),
        NestedScrollSource.UserInput,
      )
      assertThat(state.offset).isEqualTo(if (destination == OverlaySheetValue.Hidden) 0f else -100f)
      assertThat(state.settledValue).isEqualTo(initialValue)
      var consumed = Velocity.Zero
      val fling =
        launch(clock, start = CoroutineStart.UNDISPATCHED) {
          consumed = connection.onPostFling(Velocity.Zero, Velocity(42f, direction * 80f))
        }
      clock.sendFrame(0L)
      fling.join()
      assertThat(calls).isEqualTo(1)
      assertThat(state.settledValue).isEqualTo(destination)
      assertThat(consumed).isEqualTo(Velocity.Zero)
    }
  }

  @Test
  fun inwardPostFlingCanSettleFromEitherAnchor() = runBlocking {
    for (initialValue in OverlaySheetValue.entries) {
      val clock = BroadcastFrameClock()
      val state = OverlaySheetState(initialValue, initialHeight = 100f)
      val destination =
        if (initialValue == OverlaySheetValue.Shown) OverlaySheetValue.Hidden
        else OverlaySheetValue.Shown
      var calls = 0
      val policy = OverlaySheetSettlingPolicy { _, _, _, _ ->
        calls++
        destination
      }
      val connection = state.nestedScrollConnection(policy, snap())
      val available = Velocity(42f, if (initialValue == OverlaySheetValue.Shown) 80f else -80f)
      var consumed = Velocity.Zero
      val fling =
        launch(clock, start = CoroutineStart.UNDISPATCHED) {
          consumed = connection.onPostFling(Velocity.Zero, available)
        }
      clock.sendFrame(0L)
      fling.join()
      assertThat(calls).isEqualTo(1)
      assertThat(state.settledValue).isEqualTo(destination)
      assertThat(consumed).isEqualTo(Velocity(0f, available.y))
    }
  }

  @Test
  fun zeroVerticalPostFlingLeavesSettledAnchorsAlone() = runBlocking {
    for (initialValue in OverlaySheetValue.entries) {
      val clock = BroadcastFrameClock()
      val state = OverlaySheetState(initialValue, initialHeight = 100f)
      var calls = 0
      val policy = OverlaySheetSettlingPolicy { _, _, _, _ ->
        calls++
        if (initialValue == OverlaySheetValue.Shown) OverlaySheetValue.Hidden
        else OverlaySheetValue.Shown
      }
      val connection = state.nestedScrollConnection(policy, snap())
      var consumed = Velocity.Zero
      val fling =
        launch(clock, start = CoroutineStart.UNDISPATCHED) {
          consumed = connection.onPostFling(Velocity.Zero, Velocity(42f, 0f))
        }
      clock.sendFrame(0L)
      fling.join()
      assertThat(calls).isEqualTo(0)
      assertThat(state.settledValue).isEqualTo(initialValue)
      assertThat(consumed).isEqualTo(Velocity.Zero)
    }
  }

  @Test
  fun consumedPreFlingDoesNotChooseAnotherDestinationInPostFling() = runBlocking {
    val clock = BroadcastFrameClock()
    val state = OverlaySheetState(initialHeight = 100f)
    state.touchDragState.dispatchRawDelta(30f)
    var calls = 0
    val policy = OverlaySheetSettlingPolicy { _, _, _, velocity ->
      calls++
      if (velocity < 0f) OverlaySheetValue.Shown else OverlaySheetValue.Hidden
    }
    val connection = state.nestedScrollConnection(policy, snap())
    val available = Velocity(42f, -80f)
    var consumed = Velocity.Zero
    val preFling =
      launch(clock, start = CoroutineStart.UNDISPATCHED) {
        consumed = connection.onPreFling(available)
      }
    clock.sendFrame(0L)
    preFling.join()
    assertThat(consumed).isEqualTo(Velocity(0f, -80f))
    val postFling =
      launch(clock, start = CoroutineStart.UNDISPATCHED) {
        assertThat(connection.onPostFling(consumed, available - consumed)).isEqualTo(Velocity.Zero)
      }
    clock.sendFrame(1L)
    postFling.join()
    assertThat(calls).isEqualTo(1)
    assertThat(state.settledValue).isEqualTo(OverlaySheetValue.Shown)
  }

  @Test
  fun zeroVelocityPostFlingStillSettlesBetweenAnchors() = runBlocking {
    val clock = BroadcastFrameClock()
    val state = OverlaySheetState(initialHeight = 100f)
    state.touchDragState.dispatchRawDelta(30f)
    var calls = 0
    val policy = OverlaySheetSettlingPolicy { offset, _, _, velocity ->
      calls++
      assertThat(offset).isEqualTo(-70f)
      assertThat(velocity).isEqualTo(0f)
      OverlaySheetValue.Hidden
    }
    val connection = state.nestedScrollConnection(policy, snap())
    val fling =
      launch(clock, start = CoroutineStart.UNDISPATCHED) {
        assertThat(connection.onPostFling(Velocity.Zero, Velocity.Zero)).isEqualTo(Velocity.Zero)
      }
    clock.sendFrame(0L)
    fling.join()
    assertThat(calls).isEqualTo(1)
    assertThat(state.settledValue).isEqualTo(OverlaySheetValue.Hidden)
  }

  @Test
  fun unconsumedNestedScrollLeavesAnimationRunning() = runBlocking {
    val clock = BroadcastFrameClock()
    val state = OverlaySheetState(initialHeight = 1f)
    state.draggableState.updateAnchors(
      DraggableAnchors {
        OverlaySheetValue.Shown at -100f
        OverlaySheetValue.Hidden at 0f
      },
      newTarget = OverlaySheetValue.Shown,
    )
    val animation =
      launch(clock, start = CoroutineStart.UNDISPATCHED) {
        state.updateHeight(
          height = 100f,
          target = OverlaySheetValue.Hidden,
          animationSpec = tween(1000),
        )
      }
    try {
      val connection = state.nestedScrollConnection(hideOnRelease, tween(1000))
      assertThat(connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput))
        .isEqualTo(Offset.Zero)
      assertThat(
          connection.onPostScroll(Offset.Zero, Offset(0f, 10f), NestedScrollSource.SideEffect)
        )
        .isEqualTo(Offset.Zero)
      yield()
      assertThat(state.draggableState.isAnimationRunning).isTrue()
    } finally {
      animation.cancelAndJoin()
    }
  }

  @Test
  fun nestedScrollInterruptsResizeAnimation() {
    assertNestedScrollInterruptsAnimation { state ->
      state.updateHeight(height = 200f, target = null, animationSpec = tween(1000))
    }
  }

  @Test
  fun nestedScrollInterruptsSettlingAnimation() {
    assertNestedScrollInterruptsAnimation { state ->
      state.settle(velocity = 0f, settlingPolicy = hideOnRelease, animationSpec = tween(1000))
    }
  }

  private fun assertNestedScrollInterruptsAnimation(animate: suspend (OverlaySheetState) -> Unit) =
    runBlocking {
      for (preScroll in listOf(true, false)) {
        val clock = BroadcastFrameClock()
        val state = OverlaySheetState(initialHeight = 1f)
        state.draggableState.updateAnchors(
          DraggableAnchors {
            OverlaySheetValue.Shown at -100f
            OverlaySheetValue.Hidden at 0f
          },
          newTarget = OverlaySheetValue.Shown,
        )
        state.draggableState.dispatchRawDelta(50f)
        val animation = launch(clock, start = CoroutineStart.UNDISPATCHED) { animate(state) }
        try {
          clock.sendFrame(0L)
          yield()
          val before = state.draggableState.requireOffset()
          val delta = Offset(0f, if (preScroll) -10f else 10f)
          val connection = state.nestedScrollConnection(hideOnRelease, tween(1000))
          val consumed =
            if (preScroll) connection.onPreScroll(delta, NestedScrollSource.UserInput)
            else connection.onPostScroll(Offset.Zero, delta, NestedScrollSource.UserInput)
          assertThat(consumed).isEqualTo(delta)
          yield()
          clock.sendFrame(100_000_000L)
          yield()
          assertThat(state.draggableState.requireOffset()).isWithin(.01f).of(before + delta.y)
          assertThat(state.draggableState.isAnimationRunning).isFalse()
        } finally {
          animation.cancelAndJoin()
        }
      }
    }

  @Test
  fun newUserDragReplacesCancelledDismissalTarget() = runBlocking {
    val clock = BroadcastFrameClock()
    val state = OverlaySheetState(initialHeight = 1f)
    state.draggableState.updateAnchors(
      DraggableAnchors {
        OverlaySheetValue.Shown at -100f
        OverlaySheetValue.Hidden at 0f
      },
      newTarget = OverlaySheetValue.Shown,
    )
    state.draggableState.dispatchRawDelta(50f)
    val settling =
      launch(clock, start = CoroutineStart.UNDISPATCHED) {
        state.settle(velocity = 0f, settlingPolicy = hideOnRelease, animationSpec = tween(1000))
      }
    settling.cancelAndJoin()
    val dragging =
      launch(clock, start = CoroutineStart.UNDISPATCHED) {
        state.touchDragState.drag(MutatePriority.UserInput) { awaitCancellation() }
      }
    yield()
    dragging.cancelAndJoin()
    val resize =
      launch(clock, start = CoroutineStart.UNDISPATCHED) {
        state.updateHeight(height = 200f, target = null, animationSpec = tween(1000))
      }
    try {
      assertThat(state.draggableState.targetValue).isEqualTo(OverlaySheetValue.Shown)
    } finally {
      resize.cancelAndJoin()
    }
  }

  @Test
  fun completedResizeCancellationDoesNotLoseDismissalTarget() = runBlocking {
    val clock = BroadcastFrameClock()
    val state = OverlaySheetState(initialHeight = 1f)
    state.draggableState.updateAnchors(
      DraggableAnchors {
        OverlaySheetValue.Shown at -100f
        OverlaySheetValue.Hidden at 0f
      },
      newTarget = OverlaySheetValue.Shown,
    )
    state.draggableState.dispatchRawDelta(50f)
    val settling =
      launch(clock, start = CoroutineStart.UNDISPATCHED) {
        state.settle(velocity = 0f, settlingPolicy = hideOnRelease, animationSpec = tween(1000))
      }
    val firstResize =
      launch(clock, start = CoroutineStart.UNDISPATCHED) {
        state.updateHeight(height = 200f, target = null, animationSpec = tween(1000))
      }
    settling.join()
    yield()
    assertThat(state.draggableState.anchors.positionOf(OverlaySheetValue.Shown)).isEqualTo(-200f)
    assertThat(state.draggableState.targetValue).isEqualTo(OverlaySheetValue.Hidden)

    // Exercise the ordering where the old effect finishes cancellation before its replacement runs.
    firstResize.cancelAndJoin()
    assertThat(state.draggableState.isAnimationRunning).isFalse()
    assertThat(state.draggableState.settledValue).isEqualTo(OverlaySheetValue.Shown)
    val secondResize =
      launch(clock, start = CoroutineStart.UNDISPATCHED) {
        state.updateHeight(height = 300f, target = null, animationSpec = tween(1000))
      }
    try {
      assertThat(state.draggableState.targetValue).isEqualTo(OverlaySheetValue.Hidden)
    } finally {
      secondResize.cancelAndJoin()
    }
  }
}
