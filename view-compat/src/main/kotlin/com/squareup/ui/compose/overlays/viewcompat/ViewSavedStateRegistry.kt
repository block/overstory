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

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Bridges an overlay's Compose saveable state to the AndroidX registry used by embedded Views.
 *
 * The core host supplies a keyed saveable-state scope and an inactive lifecycle. This wrapper
 * restores a registry for that lifecycle before the host activates it and renders [content]. When
 * the lifecycle owner changes, it transfers both registries' saved values and replaces the content
 * composition so View factories and saveable providers register with the new owners.
 */
@Composable
internal fun ViewSavedStateRegistry(content: @Composable () -> Unit) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val parentRegistry = LocalSaveableStateRegistry.current
  val canBeSaved: (Any) -> Boolean = { parentRegistry?.canBeSaved(it) ?: true }
  val registryState =
    rememberSaveable(saver = ViewRegistryState.saver(lifecycleOwner, canBeSaved)) {
      ViewRegistryState(lifecycleOwner, canBeSaved)
    }
  val registryOwner = registryState.ownerFor(lifecycleOwner)
  // Factories must run again so their providers belong to the replacement registry.
  key(ContentGeneration(registryState.generation)) {
    CompositionLocalProvider(
      LocalSavedStateRegistryOwner provides registryOwner,
      LocalSaveableStateRegistry provides registryState.composeRegistry,
      content = content,
    )
  }
}

/**
 * Replaces remembered content when the registry owner changes while preserving saveable key hashes.
 *
 * [key] uses equality to identify the content group, so changing [value] recreates that group and
 * its providers. A constant hash keeps generated `rememberSaveable` keys compatible with the saved
 * values copied into the new registry. Only one generation is composed in this scope at a time.
 *
 * This depends on Compose's composite-key implementation, not a public guarantee that colliding
 * keys preserve saveable identity. Retaining the registry alone is insufficient: recreated content
 * cannot restore live provider values from that registry. Keep this workaround internal and run the
 * view-compat restoration suite on every Compose upgrade. The source analysis and rejected
 * prototype are documented in docs/saveable-registry.md.
 */
private data class ContentGeneration(val value: Int) {
  override fun hashCode(): Int = 0
}

/**
 * Saves both registries for one overlay and transfers them when its lifecycle owner is replaced.
 *
 * Owner, registries, and content generation are replaced in one snapshot-state write. Composition
 * observes that state, and abandoning its snapshot discards the entire replacement together.
 */
internal class ViewRegistryState(
  lifecycleOwner: LifecycleOwner,
  private val canBeSaved: (Any) -> Boolean,
  restoredBundle: Bundle? = null,
  restoredComposeState: Map<String, List<Any?>>? = null,
  generation: Int = 0,
) {
  private var current by
    mutableStateOf(
      RegistryGeneration(
        parent = lifecycleOwner,
        owner = ComposeSaveableStateRegistryOwner(lifecycleOwner, restoredBundle),
        composeRegistry = SaveableStateRegistry(restoredComposeState, canBeSaved),
        number = generation,
      )
    )

  val generation: Int
    get() = current.number

  val composeRegistry: SaveableStateRegistry
    get() = current.composeRegistry

  fun ownerFor(lifecycleOwner: LifecycleOwner): SavedStateRegistryOwner {
    val previous = current
    if (previous.parent !== lifecycleOwner) {
      // Content needs the replacement during this composition. A snapshot-state write makes the
      // transition observable and rolls it back if this composition is abandoned.
      current =
        RegistryGeneration(
          parent = lifecycleOwner,
          owner = ComposeSaveableStateRegistryOwner(lifecycleOwner, previous.owner.save()),
          composeRegistry =
            SaveableStateRegistry(previous.composeRegistry.performSave(), canBeSaved),
          number = previous.number + 1,
        )
    }
    return current.owner
  }

  private class RegistryGeneration(
    val parent: LifecycleOwner,
    val owner: ComposeSaveableStateRegistryOwner,
    val composeRegistry: SaveableStateRegistry,
    val number: Int,
  )

  companion object {
    @Suppress("UNCHECKED_CAST")
    fun saver(
      lifecycleOwner: LifecycleOwner,
      canBeSaved: (Any) -> Boolean,
    ): Saver<ViewRegistryState, Any> =
      mapSaver(
        save = { state ->
          val current = state.current
          mapOf(
            "registry" to current.owner.save(),
            "compose" to current.composeRegistry.performSave(),
            "generation" to current.number,
          )
        },
        restore = {
          ViewRegistryState(
            lifecycleOwner,
            canBeSaved,
            it["registry"] as Bundle,
            it["compose"] as Map<String, List<Any?>>,
            it["generation"] as Int,
          )
        },
      )
  }
}

private class ComposeSaveableStateRegistryOwner(
  lifecycleOwner: LifecycleOwner,
  restoredBundle: Bundle? = null,
) : SavedStateRegistryOwner, LifecycleOwner by lifecycleOwner {
  private val controller = SavedStateRegistryController.create(this)
  override val savedStateRegistry: SavedStateRegistry = controller.savedStateRegistry

  init {
    // OverlayHost invokes its wrapper before activating the lifecycle. Restore must happen
    // before ON_CREATE; nested View hosts must then see CREATED when their content attaches.
    // See https://github.com/square/workflow-kotlin/issues/1410 for the attachment-order
    // constraint.
    controller.performRestore(restoredBundle)
  }

  fun save(): Bundle = Bundle().also(controller::performSave)
}
