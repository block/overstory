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
package com.squareup.ui.compose.overlays.viewcompat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle.State
import com.squareup.ui.compose.overlays.ExperimentalComposeOverlaysApi
import com.squareup.ui.compose.overlays.OverlayHost

/**
 * Renders keyed overlays above [coveredContent] in the same Compose hierarchy.
 *
 * The caller owns the overlay models and rendering. Keys must be unique in the current stack,
 * stable across recompositions and reorders, and saveable by the host's state registry. Each
 * overlay receives a separate saveable-state and Android View registry. Removing an overlay
 * permanently discards its saved state. Compose-only callers can use [OverlayHost] directly.
 *
 * @param overlays Entries in bottom-to-top order.
 * @param keyOf Stable identity for each entry. Duplicate or unsaveable keys are rejected.
 * @param modifier Modifier applied to the host.
 * @param maxLifecycleState Lifecycle cap for each entry, given whether it is topmost. Must return
 *   [State.CREATED], [State.STARTED], or [State.RESUMED].
 * @param coveredContent Content displayed behind the overlay stack, measured at the host's size.
 * @param overlayContent Renderer for an entry, given whether it is topmost.
 */
@ExperimentalComposeOverlaysApi
@Composable
public fun <T> ViewCompatibleOverlayHost(
  overlays: List<T>,
  keyOf: (overlay: T) -> Any,
  coveredContent: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  maxLifecycleState: (overlay: T, isTopmost: Boolean) -> State = { _, isTopmost ->
    if (isTopmost) State.RESUMED else State.CREATED
  },
  overlayContent: @Composable (overlay: T, isTopmost: Boolean) -> Unit,
) {
  OverlayHost(
    overlays = overlays,
    keyOf = keyOf,
    coveredContent = coveredContent,
    modifier = modifier,
    maxLifecycleState = maxLifecycleState,
    overlayContentWrapper = { content -> ViewSavedStateRegistry(content) },
    overlayContent = overlayContent,
  )
}
