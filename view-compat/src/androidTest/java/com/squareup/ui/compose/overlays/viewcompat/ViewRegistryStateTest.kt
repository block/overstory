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

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.testing.TestLifecycleOwner
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class ViewRegistryStateTest {
  @get:Rule val rule = createComposeRule()

  @Test
  fun ownerReplacementIsDiscardedWithItsSnapshot() {
    rule.runOnIdle {
      val firstParent = TestLifecycleOwner(initialState = State.INITIALIZED)
      val nextParent = TestLifecycleOwner(initialState = State.INITIALIZED)
      val state = ViewRegistryState(firstParent, canBeSaved = { true })
      val originalOwner = state.ownerFor(firstParent)
      val originalComposeRegistry = state.composeRegistry
      val provider = originalComposeRegistry.registerProvider("count") { 7 }

      val abandoned = Snapshot.takeMutableSnapshot()
      try {
        abandoned.enter {
          assertThat(state.ownerFor(nextParent)).isNotSameInstanceAs(originalOwner)
          assertThat(state.composeRegistry).isNotSameInstanceAs(originalComposeRegistry)
          assertThat(state.generation).isEqualTo(1)
          assertThat(state.composeRegistry.consumeRestored("count")).isEqualTo(7)
          state.composeRegistry.registerProvider("abandoned") { 99 }
        }
      } finally {
        abandoned.dispose()
      }

      assertThat(state.generation).isEqualTo(0)
      assertThat(state.composeRegistry).isSameInstanceAs(originalComposeRegistry)
      assertThat(state.ownerFor(firstParent)).isSameInstanceAs(originalOwner)
      assertThat(originalComposeRegistry.performSave()).containsExactly("count", listOf(7))

      val committed = Snapshot.takeMutableSnapshot()
      val replacement =
        try {
          committed.enter { state.ownerFor(nextParent) }.also { committed.apply().check() }
        } finally {
          committed.dispose()
        }

      assertThat(state.ownerFor(nextParent)).isSameInstanceAs(replacement)
      assertThat(state.composeRegistry).isNotSameInstanceAs(originalComposeRegistry)
      assertThat(state.generation).isEqualTo(1)
      assertThat(state.composeRegistry.consumeRestored("count")).isEqualTo(7)
      assertThat(state.composeRegistry.consumeRestored("count")).isNull()
      assertThat(state.composeRegistry.consumeRestored("abandoned")).isNull()
      provider.unregister()
      assertThat(originalComposeRegistry.performSave()).isEmpty()
    }
  }
}
