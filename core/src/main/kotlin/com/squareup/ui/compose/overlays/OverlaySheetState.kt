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

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job

/** The two destinations supported by [OverlaySheetState]. */
@ExperimentalComposeOverlaysApi
public enum class OverlaySheetValue {
  Hidden,
  Shown,
}

/** Chooses a destination on release; the caller owns positional and velocity thresholds. */
@ExperimentalComposeOverlaysApi
public fun interface OverlaySheetSettlingPolicy {
  /**
   * [offset] and [shownOffset] are pixels relative to the hidden anchor at zero. [velocity] is
   * pixels/second, positive toward hidden. [settledValue] is the last completed destination.
   */
  public fun target(
    offset: Float,
    shownOffset: Float,
    settledValue: OverlaySheetValue,
    velocity: Float,
  ): OverlaySheetValue
}

/**
 * Drag, animation, and nested-scroll coordination for a vertical two-anchor sheet.
 *
 * Shown is at minus the measured height; hidden is at zero. Direct and nested drags are bounded by
 * these anchors. The renderer adds [offset] to its viewport bottom when placing the sheet. Calls
 * must run on the UI thread. The caller supplies settling policy and animation specs, and owns
 * layout, styling, lifecycle of effects, and dismissal callbacks.
 *
 * [initialHeight] must be finite and positive. It sets the initial geometry before measurement;
 * call [updateHeight] with the measured height. For entry animation, callers can start with a small
 * height and animate to the measured one.
 */
@ExperimentalComposeOverlaysApi
public class OverlaySheetState(
  initialValue: OverlaySheetValue = OverlaySheetValue.Shown,
  initialHeight: Float,
) {
  internal val draggableState = AnchoredDraggableState(initialValue, anchors(initialHeight))

  /**
   * Current vertical offset in pixels, relative to the hidden anchor. Observable in composition.
   */
  public val offset: Float
    get() = draggableState.requireOffset()

  /**
   * Last anchor reached after movement finishes. Does not change merely from crossing an anchor.
   */
  public val settledValue: OverlaySheetValue
    get() = draggableState.settledValue

  /** Whether an animation currently owns movement. Use for draggable's startDragImmediately. */
  public val isAnimationRunning: Boolean
    get() = draggableState.isAnimationRunning

  // Foundation clears its transient animation target on cancellation. A replacement resize must
  // retain that destination until a new user gesture or animation chooses another one.
  private var logicalTarget = initialValue
  private var animationJob: Job? = null

  // Keep touch drags and animations under the AnchoredDraggableState's mutation lock.
  // Using draggable lets callers catch an animation on press and supply their own target policy.
  /**
   * Drag state for a vertical Foundation draggable modifier; coordinates pointer input with
   * animations.
   */
  public val touchDragState: DraggableState =
    object : DraggableState {
      override suspend fun drag(dragPriority: MutatePriority, block: suspend DragScope.() -> Unit) =
        coroutineScope {
          val scope =
            object : DragScope {
              override fun dragBy(pixels: Float) {
                draggableState.dispatchRawDelta(pixels)
              }
            }
          // Anchor updates restart anchoredDrag's lambda. Keep the pointer consumer in a sibling
          // coroutine so a restart cannot replay its last delta. Start it only after taking the
          // lock.
          val input =
            async(start = CoroutineStart.LAZY) {
              logicalTarget = draggableState.settledValue
              scope.block()
            }
          try {
            draggableState.anchoredDrag(dragPriority) { input.await() }
          } finally {
            // Cancellation of the drag (rather than an anchor restart) must stop input as well.
            input.cancel()
          }
        }

      override fun dispatchRawDelta(delta: Float) {
        draggableState.dispatchRawDelta(delta)
      }
    }

  /**
   * Updates measured geometry and animates to [target], or retains the current logical destination
   * when null. Resizing never chooses the closest new anchor. Pointer input can interrupt the
   * animation while still receiving the new geometry; callers should allow cancellation to
   * propagate.
   */
  public suspend fun updateHeight(
    height: Float,
    target: OverlaySheetValue? = null,
    animationSpec: AnimationSpec<Float>,
  ) {
    require(height.isFinite() && height > 0f) { "Sheet height must be finite and positive." }
    val destination =
      when {
        target != null -> target
        draggableState.isAnimationRunning -> draggableState.targetValue
        else -> logicalTarget
      }
    try {
      animateTo(destination, animationSpec, height)
    } catch (interrupted: NestedScrollCancellationException) {
      // Geometry is already updated; restoring anchors here would snap away the nested input.
      throw interrupted
    } catch (cancelled: CancellationException) {
      // A held pointer has higher mutation priority than the resize animation. The gesture must
      // still receive the new geometry and settle against it on release. If this effect itself
      // was cancelled (e.g. by another resize), leave the replacement effect in charge.
      currentCoroutineContext().ensureActive()
      draggableState.updateAnchors(anchors(height), newTarget = destination)
      throw cancelled
    }
  }

  private suspend fun animateTo(
    target: OverlaySheetValue,
    animationSpec: AnimationSpec<Float>,
    height: Float? = null,
  ) = coroutineScope {
    val job = currentCoroutineContext().job
    try {
      draggableState.anchoredDrag(targetValue = target) { _, latestTarget ->
        animationJob = job
        logicalTarget = latestTarget
        if (height != null) {
          // Holding the drag lock lets Foundation restart on new anchors without snapping first.
          draggableState.updateAnchors(anchors(height), newTarget = latestTarget)
        }
        animate(
          initialValue = draggableState.requireOffset(),
          targetValue = draggableState.anchors.positionOf(latestTarget),
          animationSpec = animationSpec,
        ) { value, velocity ->
          dragTo(value, velocity)
        }
      }
    } finally {
      if (animationJob === job) animationJob = null
    }
  }

  private fun dragFromNestedScroll(delta: Float): Float {
    val offset = draggableState.requireOffset()
    val anchors = draggableState.anchors
    val newOffset = (offset + delta).coerceIn(anchors.minPosition(), anchors.maxPosition())
    if (newOffset == offset) return 0f

    // Nested scroll callbacks cannot suspend to take the drag lock. Cancel our animation scope
    // before consuming input so its next frame cannot overwrite the movement. Deltas that the
    // sheet cannot consume leave the animation alone.
    animationJob?.cancel(NestedScrollCancellationException())
    logicalTarget = draggableState.settledValue
    return draggableState.dispatchRawDelta(delta)
  }

  private class NestedScrollCancellationException : CancellationException()

  /** Animates to the caller-selected release destination, starting with zero animation velocity. */
  public suspend fun settle(
    velocity: Float,
    settlingPolicy: OverlaySheetSettlingPolicy,
    animationSpec: AnimationSpec<Float>,
  ) {
    val target =
      settlingPolicy.target(
        offset = draggableState.requireOffset(),
        shownOffset = draggableState.anchors.positionOf(OverlaySheetValue.Shown),
        settledValue = draggableState.settledValue,
        velocity = velocity,
      )
    // Release velocity selects the destination; the supplied animation runs without a decay phase.
    animateTo(target, animationSpec)
  }

  /**
   * Consumes upward user input before the child and leftover input after it. Upward flings settle a
   * displaced sheet before the child; remaining flings settle it afterward. A zero-vertical
   * post-fling settles only between anchors, leaving an anchored destination alone. Consumed nested
   * input cancels active sheet animations. Outward flings at either anchor remain unconsumed. Only
   * vertical motion is consumed. Recreate the connection when policy or animation spec changes.
   */
  public fun nestedScrollConnection(
    settlingPolicy: OverlaySheetSettlingPolicy,
    animationSpec: AnimationSpec<Float>,
  ): NestedScrollConnection =
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0 && source == NestedScrollSource.UserInput) {
          Offset(0f, dragFromNestedScroll(available.y))
        } else Offset.Zero

      override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
      ): Offset =
        if (source == NestedScrollSource.UserInput) {
          Offset(0f, dragFromNestedScroll(available.y))
        } else Offset.Zero

      override suspend fun onPreFling(available: Velocity): Velocity =
        if (
          available.y < 0 &&
            draggableState.requireOffset() >
              draggableState.anchors.positionOf(OverlaySheetValue.Shown)
        ) {
          settle(available.y, settlingPolicy, animationSpec)
          Velocity(0f, available.y)
        } else Velocity.Zero

      override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val offset = draggableState.requireOffset()
        val anchors = draggableState.anchors
        val outwardAtBoundary =
          (available.y < 0f && offset <= anchors.minPosition()) ||
            (available.y > 0f && offset >= anchors.maxPosition())
        // A settled boundary cannot use outward velocity. A drag that just reached the opposite
        // anchor must still finish settling so its logical destination catches up with its offset.
        if (outwardAtBoundary && offset == anchors.positionOf(draggableState.settledValue)) {
          return Velocity.Zero
        }
        val betweenAnchors = offset > anchors.minPosition() && offset < anchors.maxPosition()
        // Pre-fling may have already settled and consumed Y. Do not ask the policy to select a
        // second destination, but still finish a displaced sheet after a zero-velocity release.
        if (available.y != 0f || betweenAnchors) {
          settle(available.y, settlingPolicy, animationSpec)
        }
        // Leave outward velocity for a parent scroller or overscroll effect, even when we had
        // to finish settling the preceding drag.
        return if (outwardAtBoundary) Velocity.Zero else Velocity(0f, available.y)
      }
    }

  public companion object {
    /**
     * Saves only the last settled destination; restored geometry starts at [initialHeight] again.
     */
    public fun saver(initialHeight: Float): Saver<OverlaySheetState, String> =
      Saver<OverlaySheetState, String>(
        save = { it.draggableState.settledValue.name },
        restore = { OverlaySheetState(OverlaySheetValue.valueOf(it), initialHeight) },
      )

    private fun anchors(height: Float): DraggableAnchors<OverlaySheetValue> {
      require(height.isFinite() && height > 0f) { "Sheet height must be finite and positive." }
      return DraggableAnchors {
        OverlaySheetValue.Shown at -height
        OverlaySheetValue.Hidden at 0f
      }
    }
  }
}
