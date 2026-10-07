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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun rememberOverlayLifecycleOwner(maxLifecycleState: State): OverlayLifecycleOwner {
  require(maxLifecycleState.isAtLeast(State.CREATED)) {
    "Overlay lifecycle cap must be CREATED, STARTED, or RESUMED; was $maxLifecycleState."
  }
  val parent = LocalLifecycleOwner.current
  val owner = remember(parent) { OverlayLifecycleOwner(parent, maxLifecycleState) }
  SideEffect { owner.updateMaxLifecycleState(maxLifecycleState) }

  DisposableEffect(owner) {
    val observer = LifecycleEventObserver { _, _ -> owner.updateParentState() }
    parent.lifecycle.addObserver(observer)
    owner.updateParentState()
    onDispose {
      parent.lifecycle.removeObserver(observer)
      owner.destroy()
    }
  }
  return owner
}

/** An entry's lifecycle starts only after its optional saved-state integration has attached. */
internal class OverlayLifecycleOwner(
  private val parent: LifecycleOwner,
  private var maxLifecycleState: State,
) : LifecycleOwner {
  override val lifecycle = LifecycleRegistry(this)
  private var activated = false

  fun activate() {
    if (!activated) {
      activated = true
      updateParentState()
    }
  }

  fun updateMaxLifecycleState(state: State) {
    maxLifecycleState = state
    updateParentState()
  }

  fun updateParentState() {
    if (activated && lifecycle.currentState != State.DESTROYED) {
      val state = minOf(parent.lifecycle.currentState, maxLifecycleState)
      if (state == State.DESTROYED) destroy() else lifecycle.currentState = state
    }
  }

  fun destroy() {
    // LifecycleRegistry cannot go straight from INITIALIZED to DESTROYED. An entry disposed
    // before its parent is created must still deliver destruction without ever starting.
    if (lifecycle.currentState == State.INITIALIZED) lifecycle.currentState = State.CREATED
    lifecycle.currentState = State.DESTROYED
  }
}
