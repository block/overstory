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

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.FocusTargetModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.requestFocus

/**
 * Makes this overlay a focus target when [enabled]. When [requestInitialFocus] becomes true, it
 * requests focus once unless the target or one of its descendants already has focus.
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

private data class OverlayFocusElement(val requestInitialFocus: Boolean) :
  ModifierNodeElement<OverlayFocusNode>() {
  override fun create(): OverlayFocusNode = OverlayFocusNode(requestInitialFocus)

  override fun update(node: OverlayFocusNode) {
    node.update(requestInitialFocus)
  }
}

private class OverlayFocusNode(private var requestInitialFocus: Boolean) :
  DelegatingNode(), SemanticsModifierNode {
  private var overlayHasFocus = false
  private val focusTargetNode = delegate(FocusTargetModifierNode(onFocusChange = ::onFocusChange))
  private val requestFocusAction = { focusTargetNode.requestFocus() }

  override fun onAttach() {
    scheduleInitialFocusRequest()
  }

  override fun onDetach() {
    overlayHasFocus = false
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

  private fun onFocusChange(previous: FocusState, current: FocusState) {
    if (!isAttached) return
    overlayHasFocus = current.hasFocus
    if (previous.isFocused != current.isFocused) invalidateSemantics()
  }

  private fun scheduleInitialFocusRequest() {
    if (!requestInitialFocus) return
    sideEffect {
      // Keep focus claimed by overlay content. Otherwise, move it off the covered content.
      if (isAttached && requestInitialFocus && !overlayHasFocus) focusTargetNode.requestFocus()
    }
  }
}
