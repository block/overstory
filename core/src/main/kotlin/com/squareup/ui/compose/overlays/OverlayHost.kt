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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Renders keyed overlays above [coveredContent] in the same Compose hierarchy.
 *
 * The caller owns the models, navigation, rendering, and input policy. Entries are rendered in
 * bottom-to-top order. Each entry receives its own Compose saveable-state namespace and lifecycle,
 * capped by both its parent and [maxLifecycleState]. Removing an entry destroys its lifecycle and
 * discards its saved state. Reordering entries preserves both by key.
 *
 * This host does not install Android View saved-state owners. View interoperability can be added
 * through [overlayContentWrapper]. Compose-only callers can use the default wrapper:
 * ```
 * OverlayHost(
 *   overlays = dialogs,
 *   keyOf = { it.id },
 *   coveredContent = { MainContent() },
 * ) { dialog, isTopmost ->
 *   DialogContent(dialog, isTopmost)
 * }
 * ```
 *
 * @param overlays Entries in bottom-to-top order.
 * @param keyOf Stable identity for each entry, unique in the current stack and saveable by the
 *   host's state registry. Duplicate or unsaveable keys are rejected.
 * @param coveredContent Content displayed behind the overlay stack, measured at the host's size.
 * @param modifier Modifier applied to the host.
 * @param maxLifecycleState Lifecycle cap for each entry, given whether it is topmost. Must return
 *   [State.CREATED], [State.STARTED], or [State.RESUMED].
 * @param overlayContentWrapper Optional per-entry state integration. Runs inside the entry's
 *   saveable-state and lifecycle scopes, before the lifecycle is first activated, so an integration
 *   can attach its saved-state registry. It must invoke its content exactly once in the same
 *   composition; the lifecycle is activated before that content renders. Keep the wrapper's
 *   composition structure stable for the lifetime of an entry.
 * @param overlayContent Renderer for an entry, given whether it is topmost.
 */
@ExperimentalComposeOverlaysApi
@Composable
public fun <T> OverlayHost(
  overlays: List<T>,
  keyOf: (overlay: T) -> Any,
  coveredContent: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  maxLifecycleState: (overlay: T, isTopmost: Boolean) -> State = { _, isTopmost ->
    if (isTopmost) State.RESUMED else State.CREATED
  },
  overlayContentWrapper: @Composable (content: @Composable () -> Unit) -> Unit = { content ->
    content()
  },
  overlayContent: @Composable (overlay: T, isTopmost: Boolean) -> Unit,
) {
  val keys = overlays.map(keyOf)
  require(keys.distinct().size == keys.size) { "Overlay keys must be unique within the stack." }
  val saveableStateRegistry = LocalSaveableStateRegistry.current
  keys.forEach { overlayKey ->
    require(saveableStateRegistry?.canBeSaved(overlayKey) != false) {
      "Overlay key $overlayKey cannot be saved by the host state registry."
    }
  }

  Box(modifier.fillMaxSize()) {
    val holder = rememberSaveableStateHolder()
    Box(Modifier.fillMaxSize(), propagateMinConstraints = true) { coveredContent() }

    overlays.forEachIndexed { index, overlay ->
      val overlayKey = keys[index]
      val isTopmost = index == overlays.lastIndex
      key(overlayKey) {
        holder.SaveableStateProvider(overlayKey) {
          val lifecycleOwner = rememberOverlayLifecycleOwner(maxLifecycleState(overlay, isTopmost))
          CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
            overlayContentWrapper {
              // Integrations must restore their registry before ON_CREATE, while embedded Views
              // must see an activated lifecycle as soon as their content is attached.
              lifecycleOwner.activate()
              overlayContent(overlay, isTopmost)
            }
          }
        }
        DisposableEffect(Unit) { onDispose { holder.removeState(overlayKey) } }
      }
    }
  }
}
