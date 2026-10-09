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

import android.view.View
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusPropertiesModifierNode
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.FocusTargetModifierNode
import androidx.compose.ui.focus.Focusability
import androidx.compose.ui.focus.invalidateFocusProperties
import androidx.compose.ui.focus.restoreFocusedChild
import androidx.compose.ui.focus.saveFocusedChild
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.TraversableNode.Companion.TraverseDescendantsAction.ContinueTraversal
import androidx.compose.ui.node.TraversableNode.Companion.TraverseDescendantsAction.SkipSubtreeAndContinueTraversal
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireView
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.node.traverseDescendants
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.requestFocus
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Makes this overlay a focus target when [enabled]. When [requestInitialFocus] becomes true, it
 * requests focus once unless the target or one of its descendants already has focus.
 *
 * Inside a covered [overlayFocusLayer], the request waits until the layer is uncovered. An
 * uncovered layer that does not restore its saved focus also asks this target to request focus
 * again while [requestInitialFocus] is true. While [requestInitialFocus] is true and this target is
 * not inside a covered layer, it also requests focus whenever a covered layer in its composition,
 * or in a composition embedded in it, releases focus. This covers a request that was declined
 * because covered content had captured focus, and focus that covered content took through an
 * embedded View. Targets in separate compositions that do not contain the releasing layer do not
 * request focus.
 *
 * @param enabled Whether this modifier participates in focus.
 * @param requestInitialFocus Whether to request focus when this modifier becomes active.
 */
@ExperimentalComposeOverlaysApi
public fun Modifier.overlayFocusTarget(enabled: Boolean, requestInitialFocus: Boolean): Modifier =
  if (enabled) {
    then(OverlayFocusElement(requestInitialFocus))
  } else {
    this
  }

/**
 * Marks content that an overlay can cover, such as the content behind an [OverlayHost] or an
 * overlay below a newer one, so that focus leaves it while it is covered and returns when it is
 * uncovered.
 *
 * When the layer becomes covered, it saves which of its descendants has focus. After the next frame
 * applies its changes, it clears focus if focus is still in the layer, unless a covering overlay
 * has taken it, even if a descendant captured focus. While it is covered, focus requests cannot
 * enter it, and [overlayFocusTarget] descendants wait to request initial focus. If focus enters
 * anyway, for example because a View embedded in the layer requests focus, the layer clears it
 * again. Each time the layer clears focus, [overlayFocusTarget]s outside covered layers that want
 * initial focus request it, if they are in the layer's composition or in a composition that
 * contains it. When it is uncovered, it restores the saved descendant once after the next frame
 * applies its changes, so a covering overlay removed in that frame has released focus first. It
 * does not restore if focus has entered the layer since. If there is nothing to restore,
 * [overlayFocusTarget] descendants that want initial focus request it.
 *
 * Layers can be nested; a layer inside a covered layer is covered as well. Restoration is exact
 * down to the nearest focus target below each layer or [overlayFocusTarget]. Below that, focus
 * enters the restored target as it does for any focus request.
 *
 * [isCovered] is read in a snapshot observer. A change to snapshot state it reads applies as soon
 * as the change is applied, before the composition that caused it runs its effects. A layer in a
 * different composition from its covering overlay can therefore still save its focus before that
 * overlay requests it. Clearing waits until the next frame has applied its changes, so views
 * embedded in the layer still have focus while that frame's changes are applied and can save their
 * own focus.
 *
 * @param isCovered Whether an overlay covers this layer and should keep focus out of it.
 */
@ExperimentalComposeOverlaysApi
public fun Modifier.overlayFocusLayer(isCovered: () -> Boolean): Modifier =
  then(OverlayFocusLayerElement(isCovered))

private data class OverlayFocusElement(val requestInitialFocus: Boolean) :
  ModifierNodeElement<OverlayFocusNode>() {
  override fun create(): OverlayFocusNode = OverlayFocusNode(requestInitialFocus)

  override fun update(node: OverlayFocusNode) {
    node.update(requestInitialFocus)
  }
}

private data class OverlayFocusLayerElement(val isCovered: () -> Boolean) :
  ModifierNodeElement<OverlayFocusLayerNode>() {
  override fun create(): OverlayFocusLayerNode = OverlayFocusLayerNode(isCovered)

  override fun update(node: OverlayFocusLayerNode) {
    node.update(isCovered)
  }
}

/** A node that an [OverlayFocusLayerNode] saves focus through and asks to restore it. */
private sealed interface OverlayFocusParticipant : TraversableNode, FocusRequesterModifierNode {
  val hasFocus: Boolean

  override val traverseKey: Any
    get() = OverlayFocusTraverseKey
}

private object OverlayFocusTraverseKey

private class OverlayFocusNode(private var requestInitialFocus: Boolean) :
  DelegatingNode(), SemanticsModifierNode, ObserverModifierNode, OverlayFocusParticipant {
  private var overlayHasFocus = false
  private val focusTargetNode = delegate(FocusTargetModifierNode(onFocusChange = ::onFocusChange))
  private val requestFocusAction = { focusTargetNode.requestFocus() }

  override val hasFocus: Boolean
    get() = focusTargetNode.focusState.hasFocus

  /** Whether this target itself has focus, rather than one of its descendants. */
  val isFocused: Boolean
    get() = focusTargetNode.focusState.isFocused

  override fun onAttach() {
    observeFocusReleases()
    scheduleInitialFocusRequest()
  }

  override fun onDetach() {
    overlayHasFocus = false
  }

  override fun onObservedReadsChanged() {
    observeFocusReleases()
    requestWantedFocus()
  }

  override fun SemanticsPropertyReceiver.applySemantics() {
    focused = focusTargetNode.focusState.isFocused
    requestFocus(action = requestFocusAction)
  }

  fun update(requestInitialFocus: Boolean) {
    val shouldRequestFocus = !this.requestInitialFocus && requestInitialFocus
    this.requestInitialFocus = requestInitialFocus
    if (shouldRequestFocus && isAttached) scheduleInitialFocusRequest()
  }

  /** Restores focus to this target for a layer that saved it as focused. */
  fun requestFocus(): Boolean = focusTargetNode.requestFocus()

  /** Requests initial focus for a layer that was uncovered without restoring focus. */
  fun requestInitialFocusAfterUncover() {
    requestWantedFocus()
  }

  private fun onFocusChange(previous: FocusState, current: FocusState) {
    if (!isAttached) return
    overlayHasFocus = current.hasFocus
    if (previous.isFocused != current.isFocused) invalidateSemantics()
  }

  private fun scheduleInitialFocusRequest() {
    if (!requestInitialFocus) return
    // A covered layer asks again once it is uncovered or once it releases focus.
    sideEffect { if (isAttached) requestWantedFocus() }
  }

  private fun observeFocusReleases() {
    observeReads { overlayFocusReleases(requireView()).count.intValue }
  }

  /**
   * Keeps focus claimed by overlay content. Otherwise, moves it off covered content, unless this
   * target is covered itself.
   */
  private fun requestWantedFocus() {
    if (requestInitialFocus && !overlayHasFocus && !isInCoveredLayer()) {
      focusTargetNode.requestFocus()
    }
  }
}

private class OverlayFocusLayerNode(private var isCovered: () -> Boolean) :
  DelegatingNode(),
  ObserverModifierNode,
  FocusPropertiesModifierNode,
  CompositionLocalConsumerModifierNode,
  OverlayFocusParticipant {
  private val focusGroupNode =
    delegate(
      FocusTargetModifierNode(focusability = Focusability.Never, onFocusChange = ::onFocusChange)
    )
  private var hasSavedFocus = false
  /** The [OverlayFocusNode] that itself had focus when this layer was last covered, if any. */
  private var savedFocusedTarget: OverlayFocusNode? = null
  private var pendingClear: Job? = null
  private var pendingRestore: Job? = null

  /** Whether this layer itself is covered, regardless of the layers around it. */
  var covered = false
    private set

  override val hasFocus: Boolean
    get() = focusGroupNode.focusState.hasFocus

  override fun onAttach() {
    updateCoverage()
  }

  override fun onDetach() {
    covered = false
    hasSavedFocus = false
    savedFocusedTarget = null
    pendingClear = null
    pendingRestore = null
  }

  override fun onObservedReadsChanged() {
    updateCoverage()
  }

  override fun applyFocusProperties(focusProperties: FocusProperties) {
    if (covered) focusProperties.onEnter = { cancelFocusChange() }
  }

  fun update(isCovered: () -> Boolean) {
    this.isCovered = isCovered
    if (isAttached) updateCoverage()
  }

  private fun onFocusChange(previous: FocusState, current: FocusState) {
    if (!isAttached || !covered || pendingClear != null) return
    // Focus requests cannot enter a covered layer, but a View embedded in it can take focus
    // directly. Clear it once the focus change that moved it here has finished.
    if (current.hasFocus && !previous.hasFocus) {
      pendingClear = coroutineScope.launch { releaseFocusIfStillCovered() }
    }
  }

  /** Restores this layer's saved focus, if any, for an ancestor layer that has none to restore. */
  fun restoreSavedFocusAfterUncover(): Boolean {
    if (!hasSavedFocus) return false
    hasSavedFocus = false
    val focusedTarget = savedFocusedTarget
    savedFocusedTarget = null
    // A restore request follows the child each focus target saved last, which for a target that
    // had focus itself can be a child saved in an earlier cover.
    if (focusedTarget != null) return focusedTarget.isAttached && focusedTarget.requestFocus()
    return restoreFocusedChild()
  }

  private fun updateCoverage() {
    var isCoveredNow = false
    observeReads { isCoveredNow = isCovered() }
    if (isCoveredNow == covered) return
    covered = isCoveredNow
    invalidateFocusProperties()
    if (isCoveredNow) cover() else uncover()
  }

  private fun cover() {
    pendingRestore?.cancel()
    pendingRestore = null
    if (!hasFocus) return
    // A restore request only reaches the nearest focus target below a node, so every participant
    // that leads to the focused descendant saves its own step on the way down.
    var focusedTarget: OverlayFocusNode? = null
    traverseDescendants(OverlayFocusTraverseKey) { participant ->
      if (participant is OverlayFocusNode && participant.isFocused) {
        focusedTarget = participant
      } else if ((participant as OverlayFocusParticipant).hasFocus) {
        participant.saveFocusedChild()
      }
      ContinueTraversal
    }
    savedFocusedTarget = focusedTarget
    hasSavedFocus = focusedTarget != null || saveFocusedChild()
    // Clear once the frame that applies the coverage has finished, rather than while the change is
    // applied or before it is: until then, embedded views saving their own focus and the covering
    // overlay requesting focus still see focus where it was.
    pendingClear =
      coroutineScope.launch {
        awaitFrameApplied()
        releaseFocusIfStillCovered()
      }
  }

  private fun releaseFocusIfStillCovered() {
    pendingClear = null
    if (!covered || !hasFocus) return
    // Force releases focus that a descendant captured, which declined the covering overlay's
    // request. Overlay focus targets that want focus request it again once it is released.
    currentValueOf(LocalFocusManager).clearFocus(force = true)
    notifyFocusReleased()
  }

  private fun uncover() {
    pendingClear?.cancel()
    pendingClear = null
    // A layer still inside a covered layer is restored, if needed, once that layer is uncovered.
    if (isInCoveredLayer()) return
    pendingRestore =
      coroutineScope.launch {
        awaitFrameApplied()
        restoreFocusAfterUncover()
      }
  }

  private fun restoreFocusAfterUncover() {
    pendingRestore = null
    // An enclosing layer that is still covered asks this layer to restore once it is uncovered.
    if (covered || isInCoveredLayer()) return
    if (hasFocus) {
      hasSavedFocus = false
      return
    }
    if (restoreSavedFocusAfterUncover() || hasFocus) return
    // Nested layers that were uncovered while this layer was covered kept their saved focus.
    forEachUncoveredParticipant { participant ->
      if (participant is OverlayFocusLayerNode && !hasFocus) {
        participant.restoreSavedFocusAfterUncover()
      }
    }
    if (hasFocus) return
    forEachUncoveredParticipant { participant ->
      if (participant is OverlayFocusNode) participant.requestInitialFocusAfterUncover()
    }
  }

  private fun forEachUncoveredParticipant(block: (OverlayFocusParticipant) -> Unit) {
    traverseDescendants(OverlayFocusTraverseKey) { participant ->
      if (participant is OverlayFocusLayerNode && participant.covered) {
        SkipSubtreeAndContinueTraversal
      } else {
        block(participant as OverlayFocusParticipant)
        ContinueTraversal
      }
    }
  }
}

/**
 * Suspends until the next frame has recomposed and applied its changes. A snapshot change observed
 * between frames is applied in the next frame. The frame resumes this coroutine through its
 * dispatcher, after the frame's recomposition and apply.
 */
private suspend fun awaitFrameApplied() {
  withFrameNanos {}
}

/**
 * Counts the times a covered [overlayFocusLayer] in the composition of one view, or in a
 * composition embedded in it, released focus. A layer can release focus that an overlay in a
 * composition containing it wants, such as an overlay covering a view that hosts the layer.
 */
private class OverlayFocusReleases {
  val count = mutableIntStateOf(0)
}

private fun overlayFocusReleases(view: View): OverlayFocusReleases =
  view.getTag(R.id.overstory_overlay_focus_releases) as OverlayFocusReleases?
    ?: OverlayFocusReleases().also { view.setTag(R.id.overstory_overlay_focus_releases, it) }

/** Notifies overlay focus targets in this node's composition and the compositions containing it. */
private fun DelegatableNode.notifyFocusReleased() {
  var view: View? = requireView()
  while (view != null) {
    val releases = view.getTag(R.id.overstory_overlay_focus_releases) as OverlayFocusReleases?
    if (releases != null) releases.count.intValue++
    view = view.parent as? View
  }
}

/** Whether this node is inside an [overlayFocusLayer] that is covered. */
private fun TraversableNode.isInCoveredLayer(): Boolean {
  var isCovered = false
  traverseAncestors(OverlayFocusTraverseKey) { ancestor ->
    isCovered = ancestor is OverlayFocusLayerNode && ancestor.covered
    !isCovered
  }
  return isCovered
}
