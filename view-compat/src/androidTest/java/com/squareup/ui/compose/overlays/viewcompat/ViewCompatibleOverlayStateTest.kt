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

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.google.common.truth.Truth.assertThat
import com.squareup.ui.compose.overlays.ExperimentalComposeOverlaysApi
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalComposeOverlaysApi::class)
class ViewCompatibleOverlayStateTest {

  @get:Rule val rule = createComposeRule()

  @Test
  fun coveredLifecycleIsCappedAndResumesWhenUncovered() {
    val maxLifecycleState = mutableStateOf(State.CREATED)
    lateinit var childOwner: LifecycleOwner

    rule.setContent {
      ViewCompatibleOverlayHost(
        overlays = listOf("overlay"),
        keyOf = { it },
        coveredContent = {},
        maxLifecycleState = { _, _ -> maxLifecycleState.value },
      ) { _, _ ->
        childOwner = LocalLifecycleOwner.current
      }
    }

    rule.runOnIdle {
      assertThat(childOwner.lifecycle.currentState).isEqualTo(State.CREATED)
      maxLifecycleState.value = State.RESUMED
    }
    rule.runOnIdle { assertThat(childOwner.lifecycle.currentState).isEqualTo(State.RESUMED) }
  }

  @Test
  fun overlayGetsItsOwnViewCompatibleSavedStateRegistry() {
    lateinit var parentOwner: SavedStateRegistryOwner
    lateinit var childOwner: SavedStateRegistryOwner

    rule.setContent {
      parentOwner = LocalSavedStateRegistryOwner.current
      ViewCompatibleOverlayHost(
        overlays = listOf("overlay"),
        keyOf = { it },
        coveredContent = {},
      ) { _, _ ->
        childOwner = LocalSavedStateRegistryOwner.current
      }
    }

    rule.runOnIdle {
      assertThat(childOwner).isNotSameInstanceAs(parentOwner)
      assertThat(childOwner.lifecycle.currentState).isEqualTo(State.RESUMED)
    }
  }

  @Test
  fun startedLifecycleCapCanResume() {
    val maxLifecycleState = mutableStateOf(State.STARTED)
    lateinit var owner: LifecycleOwner
    rule.setContent {
      ViewCompatibleOverlayHost(
        overlays = listOf("overlay"),
        keyOf = { it },
        coveredContent = {},
        maxLifecycleState = { _, _ -> maxLifecycleState.value },
      ) { _, _ ->
        owner = LocalLifecycleOwner.current
      }
    }
    rule.runOnIdle {
      assertThat(owner.lifecycle.currentState).isEqualTo(State.STARTED)
      maxLifecycleState.value = State.RESUMED
    }
    rule.runOnIdle { assertThat(owner.lifecycle.currentState).isEqualTo(State.RESUMED) }
  }

  @Test
  fun rejectsInitializedLifecycleCap() {
    assertInvalidLifecycleCap(State.INITIALIZED)
  }

  @Test
  fun rejectsDestroyedLifecycleCap() {
    assertInvalidLifecycleCap(State.DESTROYED)
  }

  private fun assertInvalidLifecycleCap(state: State) {
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        rule.setContent {
          ViewCompatibleOverlayHost(
            overlays = listOf("overlay"),
            keyOf = { it },
            coveredContent = {},
            maxLifecycleState = { _, _ -> state },
          ) { _, _ ->
          }
        }
        rule.waitForIdle()
      }
    assertThat(error).hasMessageThat().contains("CREATED, STARTED, or RESUMED")
  }
}
