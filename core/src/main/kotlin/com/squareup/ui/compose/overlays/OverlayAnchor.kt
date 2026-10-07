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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import java.util.UUID

/**
 * Receives anchor registrations from [overlayAnchor]. The caller owns the registry's storage and
 * decides how to place, render, or dismiss overlays associated with an anchor.
 *
 * Callbacks run on the UI thread. Coordinates are pixels relative to the Compose root, including
 * ancestor clipping as reported by [boundsInRoot]; no window-inset or placement policy is applied.
 * Consume them in the same root coordinate space, or convert them before placing an overlay.
 */
@ExperimentalComposeOverlaysApi
public interface OverlayAnchorRegistry {
  /** Adds or updates [key] with its latest [boundsInRoot], without changing registration order. */
  public fun registerAnchor(key: String, boundsInRoot: Rect)

  /**
   * Removes [key] when its modifier is disabled, detached, reused, or moved to another registry.
   */
  public fun unregisterAnchor(key: String)
}

/**
 * Registers this layout as an overlay anchor once positioned. Its generated String key stays stable
 * across movement, resizing, and registry changes. Disabling, detaching, or reusing the modifier
 * ends the registration; a subsequent registration gets a new key. Keys are transient and are not
 * restored across host recreation.
 *
 * @param registry Receives position updates and removal. Registry instances are compared by
 *   reference; replacement removes the anchor from the previous registry before registering it with
 *   the new one.
 * @param enabled Whether to register the anchor. Enabling an already positioned node uses its
 *   latest bounds without requiring another layout pass.
 */
@ExperimentalComposeOverlaysApi
public fun Modifier.overlayAnchor(
  registry: OverlayAnchorRegistry,
  enabled: Boolean = true,
): Modifier = this then OverlayAnchorElement(registry, enabled)

/**
 * Node counterpart of [overlayAnchor] for adapters that resolve their registry or policy from
 * CompositionLocals. Delegate this node and call [update] when that integration changes.
 *
 * @param registry Receives position updates and removal.
 * @param enabled Whether to register the anchor.
 */
@ExperimentalComposeOverlaysApi
public class OverlayAnchorModifierNode(
  private var registry: OverlayAnchorRegistry,
  private var enabled: Boolean = true,
) : Modifier.Node(), GlobalPositionAwareModifierNode {
  private var key: String? = null
  private var bounds: Rect? = null
  private var publishedBounds: Rect? = null
  private var refreshPositionOnAttach: Boolean = false

  override val shouldAutoInvalidate: Boolean = false

  override fun onAttach() {
    if (refreshPositionOnAttach) {
      refreshPositionOnAttach = false
      // Compose reuses the LayoutNode and may keep its placement without dispatching another
      // global-position callback. Read the live coordinates after the new item's changes apply.
      sideEffect { if (isAttached) onGloballyPositioned(requireLayoutCoordinates()) }
    }
  }

  /**
   * Updates [registry] and [enabled], then republishes the latest position after changes are
   * applied. Republishing also lets an adapter refresh metadata when the anchor has not moved.
   */
  public fun update(registry: OverlayAnchorRegistry, enabled: Boolean = true) {
    if (this.registry !== registry) {
      if (publishedBounds != null) key?.let(this.registry::unregisterAnchor)
      publishedBounds = null
      this.registry = registry
    }
    this.enabled = enabled
    if (isAttached) sideEffect { if (isAttached) publish(force = true) }
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    bounds = coordinates.boundsInRoot()
    publish()
  }

  override fun onDetach() {
    clear()
  }

  override fun onReset() {
    refreshPositionOnAttach = bounds != null
    clear()
  }

  private fun publish(force: Boolean = false) {
    if (!enabled) {
      unregister()
      return
    }
    val currentBounds = bounds ?: return
    if (force || publishedBounds != currentBounds) {
      val currentKey = key ?: UUID.randomUUID().toString().also { key = it }
      publishedBounds = currentBounds
      registry.registerAnchor(currentKey, currentBounds)
    }
  }

  private fun unregister() {
    if (publishedBounds != null) key?.let(registry::unregisterAnchor)
    key = null
    publishedBounds = null
  }

  private fun clear() {
    unregister()
    bounds = null
  }
}

private class OverlayAnchorElement(val registry: OverlayAnchorRegistry, val enabled: Boolean) :
  ModifierNodeElement<OverlayAnchorModifierNode>() {
  override fun create(): OverlayAnchorModifierNode = OverlayAnchorModifierNode(registry, enabled)

  override fun update(node: OverlayAnchorModifierNode) {
    node.update(registry, enabled)
  }

  override fun equals(other: Any?): Boolean =
    other is OverlayAnchorElement && registry === other.registry && enabled == other.enabled

  override fun hashCode(): Int = 31 * System.identityHashCode(registry) + enabled.hashCode()

  override fun InspectorInfo.inspectableProperties() {
    name = "overlayAnchor"
    properties["registry"] = registry
    properties["enabled"] = enabled
  }
}
